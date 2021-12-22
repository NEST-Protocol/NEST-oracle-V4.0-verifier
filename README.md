### NEST 4.0 BSC Automatic Verification Arbitrage Program Operating Instructions

[toc]


#### Introduction
>NEST4.0 automatic verification arbitrage program is a sample program, which cannot be used directly. It must be redeveloped according to the code logic of this program,Any problems encountered during development can be submitted on Github, and the developers of this program will answer them in time.

>The relevant parameters of this program such as the price deviation percentage, the default value is 1%, which is not necessarily the optimal verification arbitrage ratio, and the user can adjust it according to the actual situation.

>To perform hedging operations, you can implement the [HedgeService](https://github.com/NEST-Protocol/NEST-Oracle-V4.0-verifier/blob/bsc/src/main/java/com/nest/ib/service/HedgeService.java)interface by yourself.

>The main functions of the verification arbitrage program are:
   * Check account assets, unfrozen assets, and frozen assets.
   * Authorize ERC20 token.
   * Initiate validation arbitrage transactions.
   * Unfreeze quotation assets.
   * Withdraw the unfreezing assets in the contract.

#### Preparation Before Start

1. Implement price interface [PriceService](https://github.com/NEST-Protocol/NEST-Oracle-V4.0-verifier/blob/bsc/src/main/java/com/nest/ib/service/PriceService.java).
   * Provide the price of token0token1, that is, how many token1s is a token0 worth
   For hedging, please implement the hedging interface[HedgeService](https://github.com/NEST-Protocol/NEST-Oracle-V4.0-verifier/blob/bsc/src/main/java/com/nest/ib/service/HedgeService.java)。
    
2. Get ready: wallet private key and related assets and nodes.
   * Wallet private key: Generated by mnemonic, can be registered through nestDapp.。
   * assets:
   <br/>Token0, token1 and nest that need to be mortgaged for the quotation of take order, and 200000 nest shall be mortgaged for each take order.
   <br/>Gas consumption of sending transaction (BNB)
   * [BSC chain node](https://docs.binance.org/smart-chain/developer/rpc.html).

#### Start and Close

1. Deploy and run the  program: HTTPS protocol is recommended.
2. Sign in：
   * The default user name is `nest` and the password is `nestqwe123!`.
   * If you need to modify the password, you can modify login.user.name (user name) and login.user.passwd (password) in src/main/resources/application.yml.
3. Stop the verification arbitrage program：
   * Forbidden verification arbitrage before closing the verification arbitrage program, and then wait 10 minutes, and then close the window after all quotation assets are confirmed and unfreezed.

#### Related Settings

1. Node settings (required)：
   * The node address must be set first.
2. Set quote channel ID
3. After the quotation channel ID is determined, click the 'confirm' button, and the token information of token0 and token1 will be printed in the background log. Continue the follow-up operation after checking that the information is correct.
4. Set quotation private key (required):
   * Fill in the private key, the program will perform authorization checks, if not authorized, the program will automatically initiate an authorized transaction, please make sure that the authorized transaction is packaged successfully before you can open the verification arbitrage.
5. GasPrice multiple configuration (doubled on the basis of the default gasPrice, can be adjusted according to the actual situation).
6. Start the verification arbitrage:
   * After the above configuration is completed, you can start verification arbitrage.
7. Withdraw assets
   * Only closed quotation assets can be Withdrawed. To retrieve all assets, please close all quotations before Withdrawing.



#### Contract interface @BSC
| Function | Interface | 
| ---- | ---- |
| Get quotation list | [list](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L227) | 
| Exchange token1 for token0 | [takeToken0](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/NestOpenMining.sol#L358) | 
| Exchange token0 for token1 | [takeToken1](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/NestOpenMining.sol#L442) | 
| Close quotation | [close](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L233) | 
| Withdraw assets | [withdraw](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L249) | 
| Query unfreeze assets | [balanceOf](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L244) |

