/*
 * Copyright (c) 2007 9095-2458 Quebec Inc. All Rights Reserved.
 *
 * Althought this code is consider of good quality and has been tested, it is
 * provided to you WITHOUT guaranty of any kind.
 */
package com.quartz.qtrend.dom.services;

import com.quartz.qtrend.dom.*;
import com.quartz.qtrend.dom.aroon.services.AroonService;
import com.quartz.qtrend.dom.dao.StockQuoteDAO;
import com.quartz.qtrend.dom.dao.StockQuoteLoadContext;
import com.quartz.qtrend.dom.helpers.Ticker;
import com.quartz.qtrend.dom.langford.LangfordDataImpl;
import com.quartz.qtrend.dom.langford.services.LangfordDataService;
import com.quartz.qutilities.spring.transactions.QTransactionCallback;
import com.quartz.qutilities.spring.transactions.QTransactionTemplate;
import com.quartz.qutilities.util.DateUtilities;
import com.quartz.qutilities.logging.ILog;
import com.quartz.qutilities.logging.LogManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.joda.time.YearMonthDay;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * INSERT YOUR COMMENT HERE....
 *
 * @author Christian
 * @since Quartz...
 */
public class StockQuoteService
{
    static private final ILog LOG = LogManager.getLogger(StockQuoteService.class);

    private static final String SQL_GET_PREVIOUS_STOCK =
            "SELECT * " +
            "FROM STOCKQUOTES JOIN Langford ON StockQuotes.ID=Langford.REFID " +
            "WHERE TICKER=? AND PERIODSEQUENCE=?;";

    //  set by spring
    private StockQuoteDAO       stockQuoteDao;
    private LangfordDataService langfordDataService;
    private AroonService        aroonService;

    //  brand new way!
    private SimpleJdbcTemplate   jdbcTemplate = null;
    private QTransactionTemplate transactionTemplate = null;

    public StockQuoteService()
    {
    }

    public void setJdbcTemplate(SimpleJdbcTemplate pJdbcTemplate)
    {
        jdbcTemplate = pJdbcTemplate;
    }

    public void setTransactionTemplate(QTransactionTemplate pTransactionTemplate)
    {
        transactionTemplate = pTransactionTemplate;
    }

    public void setStockQuoteDao(StockQuoteDAO pStockQuoteDao)
    {
        stockQuoteDao = pStockQuoteDao;
    }

    public void setLangfordDataService(LangfordDataService pLangfordDataService)
    {
        langfordDataService = pLangfordDataService;
    }

    public void setAroonService(AroonService pAroonService)
    {
        aroonService = pAroonService;
    }

    public int deleteTicker(Ticker pTicker) throws StockException
    {
        return jdbcTemplate.update("delete from stockquotes where ticker=?;",
                                   pTicker.toString());
    }

    public void recalculate(StockQuote pStockQuote) throws StockException
    {
        calculatePriceDifference(pStockQuote);
        if (pStockQuote.hasLangfordData() == false) pStockQuote.setLangfordData(new LangfordDataImpl(pStockQuote));
        langfordDataService.calculate(pStockQuote.getLangfordData());
        calculateAverageVolume(pStockQuote);

        pStockQuote.setAroonShortTerm(aroonService.create(pStockQuote, 7));
        pStockQuote.setAroonLongTerm(aroonService.create(pStockQuote, 28));
    }

    public StockQuote load(ResultSet pResultSet, StockQuoteLoadContext pContext)
            throws StockException
    {
        try
        {
            return new LoadFullQuoteRowMapper(pContext).mapRow(pResultSet, 1);
        }
        catch (SQLException e)
        {
            throw new StockException(e);
        }
    }

    private void calculateAverageVolume(StockQuote pStockQuote) throws StockException
    {
 //       if (periodSequence < 1) throw new StockException("Invalid period sequence: " + periodSequence);

        final StockQuote previousQuote = getPreviousQuote(pStockQuote);

        if (previousQuote == null)
        {
            //  if no pervious quote, this means this quote is the first.
            assert pStockQuote.getPeriodSequence() < 1;

            pStockQuote.setAverageVolume(pStockQuote.getVolume());
        }
        else
        {

            final int previousPeriodSequence = previousQuote.getPeriodSequence();

            final int divider = (previousPeriodSequence < 29 ? previousPeriodSequence : 29);

            long total = previousQuote.getAverageVolume() * divider;
            total += pStockQuote.getVolume();
            pStockQuote.setAverageVolume(total / (divider + 1));
        }


//        final Collection<StockQuote> previousStocks = getPreviousQuotesIncludingMe(AVG_VOLUME_PERIODS);
//
//        long totalVolume = 0;
//        for (StockQuote s : previousStocks)
//        {
//            totalVolume += s.getVolume();
//        }
//
//        averageVolume = (totalVolume / AVG_VOLUME_PERIODS);
    }

    public void calculatePriceDifference(StockQuote pStockQuote) throws StockException
    {
        final StockQuote previous = getPreviousQuote(pStockQuote);
        if (previous == null)
        {
            pStockQuote.setPriceDifference(0.0f);
            return;
        }

        pStockQuote.setPriceDifference(pStockQuote.getClose().price - previous.getClose().price);
    }

    public StockQuote createNewStockQuote()
    {
        return new StockQuoteImpl();
    }

    public void saveQuoteAndIndicators(final StockQuote pStockQuote) throws StockException
    {
        transactionTemplate.execute(new QTransactionCallback<Integer>()
        {
            public Integer doInTransaction(TransactionStatus status) throws Exception
            {
                final boolean isInsert = stockQuoteDao.saveQuoteOnly(pStockQuote);

                //  langford
                if (pStockQuote.hasLangfordData())
                {
                    langfordDataService.saveLangfordDataOnly(pStockQuote, isInsert);
                }
                else
                {
                    //  failure on insert if langford not calculated
                    if (isInsert == true) throw new StockException("Inserting new Quote without Langford data present!");
                }

                //  aroon short & long terms
                if (pStockQuote.hasAroonShortTerm())
                {
                    aroonService.save(pStockQuote.getAroonShortTerm());
                }
                else
                {
                    //  failure on insert if no aroon short term
                    if (isInsert == true) throw new StockException("Inserting a new Quote without Aroon short term.");
                }

                if (pStockQuote.hasAroonLongTerm())
                {
                    aroonService.save(pStockQuote.getAroonLongTerm());
                }
                else
                {
                    //  failure on insert if no aroon long term
                    if (isInsert == true) throw new StockException("Inserting a new Quote without Aroon long term.");
                }

                return 1;
            }
        });
    }

    private StockQuote getPreviousQuote(StockQuote pStockQuote, StockQuoteNavigator pNavigator) throws StockException
    {
        //  first if previous is set in stock quote.  If yes, use it.
        StockQuote previous = pStockQuote.getPreviousStockQuote();
        if (previous != null) return previous;

        if (pNavigator != null && pNavigator.supportsGetPreviousQuote())
        {
            previous = pNavigator.getPreviousQuote(pStockQuote);
        }
        else
        {
            try
            {
                final StockQuoteLoadContext context = new StockQuoteLoadContext();
                context.setRowContainsLangford(true);

                return jdbcTemplate.queryForObject(SQL_GET_PREVIOUS_STOCK,
                                                   new LoadFullQuoteRowMapper(context),
                                                   pStockQuote.getTicker().toString(),
                                                   pStockQuote.getPeriodSequence() - 1);
            }
            catch (IncorrectResultSizeDataAccessException e)
            {
                //  find a better way to detect no previous quote than an exception...
                return null;
            }
        }

        pStockQuote.setPreviousStockQuote(previous);

        return previous;
    }

    public StockQuote getPreviousQuote(StockQuote pStockQuote) throws StockException
    {
        return getPreviousQuote(pStockQuote, pStockQuote.getStockQuoteNavigator());
    }

    public StockQuote getLatestQuote(final Ticker pTicker)
    {
        final String SQL =
                "select stockquotes.*, langford.* " +
                "from   stockquotes " +
                "       inner join " +
                "       langford " +
                "       on refid=id " +
                "where ticker=? " +
                "and date=(select max(date) from stockquotes where ticker=?);";

        final StockQuoteLoadContext context = new StockQuoteLoadContext(true, false, false);

        return transactionTemplate.execute(new QTransactionCallback<StockQuote>()
        {
            public StockQuote doInTransaction(TransactionStatus status) throws Exception
            {
                final List<StockQuote> info = jdbcTemplate.query(SQL,
                                                                 new LoadFullQuoteRowMapper(context),
                                                                 pTicker.toString(), pTicker.toString());
                if (info.isEmpty()) return null;
                if (info.size() == 1) return info.get(0);
                throw new StockException("Many records found for latest, for ticker " + pTicker);
            }
        });
    }

    public String getTickerName(Ticker pTicker)
    {
        if (pTicker == null) return null;

        try
        {
            return jdbcTemplate.queryForObject("select name from names where ticker=?;", String.class, pTicker.toString().replaceAll("/", "."));
        }
        catch (EmptyResultDataAccessException e)
        {
            return pTicker.toString();
        }
    }

    private Map<Ticker, FullTickerName> loadTickerNames()
    {
        final List<FullTickerName> loaded = jdbcTemplate.query("SELECT stockexchange,ticker, name FROM Names;",
                                                               new FullTickerNameRowMapper());

        final Map<Ticker, FullTickerName> tickerNames = new HashMap<Ticker, FullTickerName>();

        for (final FullTickerName n : loaded)
        {
            tickerNames.put(n.ticker, n);
        }

        return tickerNames;
    }

    public void saveTickerNames(Map<Ticker, FullTickerName> pNames)
    {
        final Map<Ticker, FullTickerName> currentNames = loadTickerNames();

        //  update names
        currentNames.putAll(pNames);

        transactionTemplate.execute(new TransactionCallbackWithoutResult()
        {
            protected void doInTransactionWithoutResult(TransactionStatus status)
            {
                //  delete current names
                jdbcTemplate.update("DELETE FROM Names;");

                for (FullTickerName fullTickerName : currentNames.values())
                {
                    jdbcTemplate.update("INSERT INTO Names (stockexchange,ticker, name) VALUES (?, ?, ?);",
                                        fullTickerName.exchange.toString(),
                                        fullTickerName.ticker.toString(),
                                        fullTickerName.name);
                }
            }
        });
    }

    public void deleteQuote(Ticker pTicker, YearMonthDay pEndDate)
    {
        if (jdbcTemplate.update("delete from stockquotes where ticker=? and date=?;", pTicker.toString(), DateUtilities.toJavaSqlDate(pEndDate)) != 1)
        {
            LOG.warn("No quote found for ticker " + pTicker + " on " + pEndDate);
        }
    }
}
