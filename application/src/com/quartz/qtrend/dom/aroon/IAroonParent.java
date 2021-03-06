/*
 * Copyright (c) 2007 9095-2458 Quebec Inc. All Rights Reserved.
 *
 * Althought this code is consider of good quality and has been tested, it is
 * provided to you WITHOUT guaranty of any kind.
 */
package com.quartz.qtrend.dom.aroon;

import com.quartz.qtrend.dom.helpers.Ticker;

/**
 * INSERT YOUR COMMENT HERE....
 *
 * @author Christian
 * @since Quartz...
 */
public interface IAroonParent
{
    int getPeriodSequence();

    Ticker getTicker();

    long getId();
}
