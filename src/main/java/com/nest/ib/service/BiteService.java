package com.nest.ib.service;

import com.nest.ib.model.Wallet;


public interface BiteService {

    void bite(Wallet wallet);

    void closePriceSheets(Wallet wallet);

}
