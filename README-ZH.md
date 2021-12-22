### NEST4.0 BSC自动验证套利程序操作说明

[toc]


#### 介绍
>NEST4.0自动验证套利程序是一个示例程序，无法直接使用，可以根据本程序代码逻辑进行二次开发，开发中遇到任何问题，均可在github上提交问题，本程序开发人员会一一解答。

>本程序相关参数如价格偏离百分比，默认值为1%，并不一定是最优的验证套利比例，用户可根据实际情况自行调整。

>如需进行对冲操作，可自行实现[HedgeService]()接口。

>验证套利程序主要功能有：
   * 检查账户资产、解冻资产、冻结资产情况。
   * ERC20代币授权。
   * 发起验证套利交易。
   * 解冻报价资产。
   * 取出合约内解冻资产。

#### 启动前准备

1. 实现价格接口[PriceService](https://github.com/NEST-Protocol/NEST-Oracle-V4.0-minner/blob/bsc/src/main/java/com/nest/ib/service/PriceService.java)。
   * 获取token0Token1的价格，即1个token0价值多少个token1
   如需对冲，请实现对冲接口[HedgeService](https://github.com/NEST-Protocol/NEST-Oracle-V4.0-minner/blob/bsc/src/main/java/com/nest/ib/service/HedgeService.java)。
    
2. 准备好：钱包私钥及相关资产、节点。
   * 钱包私钥：
   通过助记词生成，可通过nestDapp注册。
   * 需要资产:
   <br/>token0、token1以及吃单报价需要抵押的NEST，每笔吃单需抵押20万NEST。
   <br/>发送交易的gas消耗（BNB）
   * BSC链节点。

#### 启动和关闭

1. 部署运行报价程序：建议使用https协议。
2. 登录：
   * 浏览器输入http://127.0.0.1:8099/main，会进入登录页面，默认用户名nest，密码nestqwe123!。
   * 如需修改密码，可修改src/main/resources/application.yml中的login.user.name（用户名）、login.user.passwd（密码）。
3. 关闭验证套利程序：
   * 关闭验证套利程序前先停止验证套利，然后等待10分钟，待所有报价资产确认解冻完毕后再关闭窗口。

#### 相关设置

1. 节点设置（必填）：
   * 必须优先设置节点地址。
2. 设置报价通道ID
3. 报价通道ID确定后，点击`confirm`按钮，后台日志会打印TOKEN0和TOKEN0的代币信息，检查信息无误后继续后续操作。
4. 设置报价私钥（必填）：
   * 填写私钥，程序会进行授权检查，如果未授权，程序会自动发起授权交易，请确定授权交易打包成功后方可进行报价。
5. gasPrice 倍数配置（在默认gasPrice基础上进行加倍，可根据实际情况进行调整）。
6. 开启验证套利：
   * 以上配置完成后，便可开启验证套利。
7. 取出资产
   * 只有关闭的报价单资产才能取出，如需取出所有资产，请关闭所有报价单后再进行取出操作。



#### 涉及合约接口@BSC
| 功能 | 接口 | 
| ---- | ---- |
| 获取报价单 | [list](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L227) | 
| 吃单获取token0 | [takeToken0](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/NestOpenMining.sol#L358) | 
| 吃单获取token1 | [takeToken1](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/NestOpenMining.sol#L442) | 
| 关闭报价单 | [close](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L233) | 
| 取出资产 | [withdraw](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L249) | 
| 查询解冻资产 | [balanceOf](https://github.com/NEST-Protocol/NEST-Oracle-V4.0/blob/bsc/contracts/interface/INestOpenMining.sol#L244) |

