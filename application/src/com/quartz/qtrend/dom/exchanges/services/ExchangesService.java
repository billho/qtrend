/*
 * Copyright (c) 2007 9095-2458 Quebec Inc. All Rights Reserved.
 *
 * Althought this code is consider of good quality and has been tested, it is
 * provided to you WITHOUT guaranty of any kind.
 */
package com.quartz.qtrend.dom.exchanges.services;

import com.quartz.qtrend.dom.ImportDates;
import com.quartz.qtrend.dom.StockException;
import com.quartz.qtrend.dom.exchanges.StockQuoteVariation;
import com.quartz.qtrend.dom.helpers.Ticker;
import com.quartz.qtrend.dom.services.StockQuoteVariationRowMapper;
import com.quartz.qutilities.sql.YearMonthDayByTimeMillisRowMapper;
import com.quartz.qutilities.util.DateUtilities;
import org.joda.time.YearMonthDay;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;

import java.util.List;


/**
 * INSERT YOUR COMMENT HERE....
 *
 * @author Christian
 * @since Quartz...
 */
public class ExchangesService
{
    private static final String SQL_FIND_EXCHANGE_IMPORT_DATES = "select stockexchange, min(date) as mindate, max(date) as maxdate, count(distinct date) as nbperiods from " +
                           "stockquotes group by stockexchange order by stockexchange;";
    private static final String SQL_FIND_EXISTING_DATES_WITH_LIMIT = "select distinct(date) from stockquotes order by date desc limit ?;";
    private static final String SQL_FIND_EXISTING_DATES_FOR_EXCHANGE_WITH_LIMIT = "select distinct(date) from stockquotes where stockexchange=? order by date desc limit ?;";

    private static final String SQL_FIND_STOCKS_BY_VARIATION = "select * from V_TICKERS where stockexchange=? and date >= ? and XXX <= (-1 * ?) and close >= ? and volume >= ? ORDER BY XXX;";
    private static final String SQL_FIND_PRICE_DROPS_7DAYS = SQL_FIND_STOCKS_BY_VARIATION.replaceAll("XXX", "closevar7");
    private static final String SQL_FIND_PRICE_DROPS_3DAYS = SQL_FIND_STOCKS_BY_VARIATION.replaceAll("XXX", "closevar3");
    private static final String SQL_FIND_PRICE_DROPS_1DAY = SQL_FIND_STOCKS_BY_VARIATION.replaceAll("XXX", "closevar1");

    private SimpleJdbcTemplate jdbcTemplate;

    public ExchangesService()
    {
    }

    public void setJdbcTemplate(SimpleJdbcTemplate pJdbcTemplate)
    {
        jdbcTemplate = pJdbcTemplate;
    }

    /**
     *
     * @return The lowest and highest quote dates, by exchange
     * @throws StockException If something goes wrong
     */
    public List<ImportDates> findLastImportDates() throws StockException
    {
        return jdbcTemplate.query(SQL_FIND_EXCHANGE_IMPORT_DATES, new FindExchangesImportDatesRowMapper());
    }

    /**
     * Finds existing dates
     *
     * @param pOptionalExchange For given exchange, null for all
     * @param pOptionalLimit Optional limit, null for none
     * @return Existing dates found
     * @throws StockException If something goes wrong
     */
    public List<YearMonthDay> findExistingDates(Ticker pOptionalExchange, Integer pOptionalLimit) throws StockException
    {
        if (pOptionalExchange != null)
        {
            return jdbcTemplate.query(SQL_FIND_EXISTING_DATES_FOR_EXCHANGE_WITH_LIMIT,
                                      new YearMonthDayByTimeMillisRowMapper(),
                                      pOptionalExchange.toString(),
                                      pOptionalLimit);
        }
        else
        {
            return jdbcTemplate.query(SQL_FIND_EXISTING_DATES_WITH_LIMIT,
                                      new YearMonthDayByTimeMillisRowMapper(),
                                      pOptionalLimit);
        }
    }

    public List<StockQuoteVariation> findPriceDrops(Ticker pExchangeName, YearMonthDay pFromDate, int pPeriod, float pMinDropPerc, float pMinimumClose, long pMinimumVolume) throws StockException
    {
        final String SQL = (pPeriod == 7
                            ? SQL_FIND_PRICE_DROPS_7DAYS
                            : (pPeriod == 3 ? SQL_FIND_PRICE_DROPS_3DAYS : SQL_FIND_PRICE_DROPS_1DAY));

        return jdbcTemplate.query(SQL,
                                  new StockQuoteVariationRowMapper(pExchangeName, null),
                                  pExchangeName.toString(),
                                  DateUtilities.toJavaSqlDate(pFromDate),
                                  pMinDropPerc,
                                  pMinimumClose,
                                  pMinimumVolume);
    }
}
