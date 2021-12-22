package com.nest.ib.task;

import com.nest.ib.model.Wallet;
import com.nest.ib.service.BiteService;

import com.nest.ib.helper.WalletHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class VerifyTask {
    @Autowired
    private BiteService biteService;

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        return taskScheduler;
    }

    /**
     * Validation: the ETH/ERC20
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 1 * 60 * 1000)
    public void bite() {
        Wallet wallet = WalletHelper.getWallet();
        if (wallet == null) return;
        biteService.bite(wallet);
    }

    /**
     * Close quotation, unfreeze assets, batch unfreeze, only quotation account can unfreeze their own quotation
     */
    @Scheduled(fixedDelay = 120 * 1000, initialDelay = 1 * 60 * 1000)
    public void close() {
        Wallet wallet = WalletHelper.getWallet();
        if (wallet == null) return;
        biteService.closePriceSheets(wallet);
    }

}
