package com.nest.ib.service;

import java.math.BigDecimal;

/**
 * @author wll
 * @date 2021/1/13 19:32
 */
public interface HedgeService {


    void sellToken0(BigDecimal token0Amount);

    void sellToken1(BigDecimal token1Amount);
}
