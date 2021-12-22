package com.nest.ib.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author wll
 * @date 2020/12/28 15:48
 */
@Component
public class VerifyState {
    private static final Logger log = LoggerFactory.getLogger(VerifyState.class);

    /**
     * Verify status, off by default
     */
    private volatile boolean open;

    /**
     * Whether to open a hedge
     */
    private boolean hedge = false;

    /**
     * Token validates the price deviation threshold:The default 1%
     */
    public volatile BigDecimal tokenBiteThreshold = new BigDecimal(0.01);

    /**
     * Minimum quantity of each batch defrost quotation
     */
    private volatile int closeMinNum = 1;

    /**
     * Number of queries each time the contract quotation list is called: Default 50
     */
    private volatile BigInteger maxFindNum = new BigInteger("50");

    public boolean isOpen() {
        return open;
    }

    public void close() {
        this.open = false;
        log.info("Verify closed");
    }

    public void open() {
        this.open = true;
        log.info("Verify enabled");
    }

    public int getCloseMinNum() {
        return closeMinNum;
    }

    public void setCloseMinNum(int closeMinNum) {
        this.closeMinNum = closeMinNum;
    }

    public BigInteger getMaxFindNum() {
        return maxFindNum;
    }

    public void setMaxFindNum(BigInteger maxFindNum) {
        this.maxFindNum = maxFindNum;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public BigDecimal getTokenBiteThreshold() {
        return tokenBiteThreshold;
    }

    public void setTokenBiteThreshold(BigDecimal tokenBiteThreshold) {
        this.tokenBiteThreshold = tokenBiteThreshold;
    }

    public boolean isHedge() {
        return hedge;
    }

    public void setHedge(boolean hedge) {
        this.hedge = hedge;
    }
}
