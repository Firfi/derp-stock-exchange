package com.loskutoff.madcow;

import org.adridadou.ethereum.BlockchainProxy;
import org.adridadou.ethereum.EthAddress;
import org.adridadou.ethereum.BlockchainProxyTest;
import org.adridadou.ethereum.EthereumFacade;
import org.adridadou.ethereum.EthereumContractInvocationHandler;
import org.adridadou.ethereum.provider.TestnetEthereumFacadeProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;


public class Contract {

    // private final String contractAddress = "0x6a96486c472b1fa77c5ddbde06d34c73cd599c8a";
    private final String contractSource = "contract mortal {\n" +
            "    /* Define variable owner of the type address*/\n" +
            "    address owner;\n" +
            "\n" +
            "    /* this function is executed at initialization and sets the owner of the contract */\n" +
            "    function mortal() { owner = msg.sender; }\n" +
            "\n" +
            "    /* Function to recover the funds on the contract */\n" +
            "    function kill() { if (msg.sender == owner) suicide(owner); }\n" +
            "}\n" +
            "\n" +
            "contract cowdb is mortal {\n" +
            "    /* TODO uint32 but we're safe until 2038 */\n" +
            "    mapping (uint => uint) values;\n" +
            "\n" +
            "    /* this runs when the contract is executed */\n" +
            "    function cowdb() public {\n" +
            "    }\n" +
            "\n" +
            "    function sendValue(uint timestamp, uint value) returns(bool successful) {\n" +
            "        values[timestamp] = value;\n" +
            "        return true;\n" +
            "    }\n" +
            "}";

    private final TestnetEthereumFacadeProvider testnet = new TestnetEthereumFacadeProvider();
    private final BlockchainProxy bcProxy = new BlockchainProxyTest();
    private final EthereumFacade ethereumProvider = new EthereumFacade(new EthereumContractInvocationHandler(bcProxy), bcProxy); // testnet.create(testnet.getKey("cow", "")); //

    public void sendValue(int k, int v) throws IOException, ExecutionException, InterruptedException {
        String contract =
                "contract myContract2 {" +
                        "  int i1;" +
                        "  function myMethod(int value) {i1 = value;}" +
                        "  function getI1() constant returns (int) {return i1;}" +
                        "}";

        // TODO published contract should be saved as ID and used by it . no time now
        EthAddress address = ethereumProvider.publishContract(contractSource);

        cowdb proxy = ethereumProvider.createContractProxy(contractSource, address, cowdb.class);
        proxy.sendValue(k, v);
    }


    private interface cowdb {
        void sendValue(int k, int v);
    }
}