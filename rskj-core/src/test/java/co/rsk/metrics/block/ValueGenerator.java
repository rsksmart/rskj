package co.rsk.metrics.block;

import co.rsk.metrics.block.tests.TestContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ValueGenerator {

    private boolean useRandom;

    //Random source
    private Random random;

    //Previously generated, random but deterministic, source
    private List<Integer> mayorityAccountsSrc;
    private List<Integer> minorityAccountsSrc;
    private List<Integer> trxSelectionSrc;
    private List<Integer> trxAmountSrc;
    private List<Boolean> trxTypeSrc;
    private List<Integer> coinbaseSrc;

    //Current indexes
    private Iterator<Integer> may,min,trxSel,trxAmt, coinbaseSel;
    private Iterator<Boolean> trxType;


    //Random caps
    private int minorityCap, mayorityCap, trxAmountCap, coinbaseCap;


    /**
     * Obtain random values with some constrains
     * @param minorityCap maximum value for the minority account, minimum being 0
     * @param mayorityCap maximum value for the mayority account, minimum being minorityCap+1
     * @param maxTrxAmount maximum value for the transaction amount, minimum being 1
     */
    public  ValueGenerator(int minorityCap, int mayorityCap, int maxTrxAmount){
        random = new Random();
        useRandom = true;
        this.minorityCap = minorityCap;
        this.mayorityCap = mayorityCap;
        this.coinbaseCap = TestContext.DATASOURCE_COINBASES_TO_GENERATE;
        trxAmountCap = maxTrxAmount;
    }

    /**
     * Obtain random values but generated from a predefined seed, with some constrains
     * @param minorityCap maximum value for the minority account, minimum being 0
     * @param mayorityCap maximum value for the mayority account, minimum being minorityCap+1
     * @param maxTrxAmount maximum value for the transaction amount, minimum being 1
     */
    public  ValueGenerator(int minorityCap, int mayorityCap, int maxTrxAmount, int seed){
        random = new Random(seed);
        useRandom = true;
        this.minorityCap = minorityCap;
        this.mayorityCap = mayorityCap;
        trxAmountCap = maxTrxAmount;
        this.coinbaseCap = TestContext.DATASOURCE_COINBASES_TO_GENERATE;

    }

    /**
     * Obtain pre-generated "random" values
     * Ideally, these source files where generated using this class
     */
    public ValueGenerator(String sourceDir) throws IOException {

        useRandom = false;

        minorityAccountsSrc = readRandomIntFile(sourceDir+"/minorityInt");
        mayorityAccountsSrc = readRandomIntFile(sourceDir+"/mayorityInt");
        trxSelectionSrc = readRandomIntFile(sourceDir+"/trxSelectionInt");
        trxAmountSrc = readRandomIntFile(sourceDir+"/trxAmountInt");
        trxTypeSrc = readRandomBoolFile(sourceDir+"/trxTypeBool");
        coinbaseSrc = readRandomIntFile(sourceDir+"/coinbaseSelectionInt");

        may = mayorityAccountsSrc.iterator();
        min = minorityAccountsSrc.iterator();
        trxSel = trxSelectionSrc.iterator();
        trxAmt = trxAmountSrc.iterator();
        trxType = trxTypeSrc.iterator();
        coinbaseSel = coinbaseSrc.iterator();
    }


    /**
     * Generate random values source files,  with some constrains
     * Files are comma-separated:
     * minorityInt: contains minority account indices
     * mayoritiInt: contains mayority account indices
     * trxSelectionInt: contains token contract selection indices (from 0 to 4, covering all available token contracts)
     * trxAmountInt: contains transaction amounts
     * trxTypeBool: contains transfer types, 0: Token transaction, 1: Coin transaction
     * @param sourceDir path where to store the different datasource files
     * @param valuesToGenerate number of different values to generate, for each file
     * @param minorityCap maximum value for the minority account, minimum being 0
     * @param mayorityCap maximum value for the mayority account, minimum being minorityCap+1
     * @param maxTrxAmount maximum value for the transaction amount, minimum being 1
     */
    public ValueGenerator(String sourceDir, int valuesToGenerate, int minorityCap, int mayorityCap, int maxTrxAmount) throws IOException {
        Random valueSource = new Random();

        generateRandomIntFile(valueSource, sourceDir+"/minorityInt", valuesToGenerate, 0, minorityCap + 1);
        generateRandomIntFile(valueSource, sourceDir+"/mayorityInt", valuesToGenerate, minorityCap + 1, mayorityCap + 1);
        generateRandomIntFile(valueSource, sourceDir+"/trxSelectionInt", valuesToGenerate, 0,5); //From 0,1,2,3,4
        generateRandomIntFile(valueSource, sourceDir+"/trxAmountInt", valuesToGenerate, 1, maxTrxAmount);
        generateRandomIntFile(valueSource, sourceDir+"/trxTypeBool", valuesToGenerate,0, 2);//0,1
        generateRandomIntFile(valueSource, sourceDir+"/coinbaseSelectionInt", valuesToGenerate, 0, TestContext.DATASOURCE_COINBASES_TO_GENERATE); //From 0 to 19

        useRandom = false;
        minorityAccountsSrc = readRandomIntFile(sourceDir+"/minorityInt");
        mayorityAccountsSrc = readRandomIntFile(sourceDir+"/mayorityInt");
        trxSelectionSrc = readRandomIntFile(sourceDir+"/trxSelectionInt");
        trxAmountSrc = readRandomIntFile(sourceDir+"/trxAmountInt");
        trxTypeSrc = readRandomBoolFile(sourceDir+"/trxTypeBool");
        coinbaseSrc = readRandomIntFile(sourceDir+"/coinbaseSelectionInt");


        may = mayorityAccountsSrc.iterator();
        min = minorityAccountsSrc.iterator();
        trxSel = trxSelectionSrc.iterator();
        trxAmt = trxAmountSrc.iterator();
        trxType = trxTypeSrc.iterator();
        coinbaseSel = coinbaseSrc.iterator();


    }


    private List<Integer> readRandomIntFile(String filePath) throws IOException{
        BufferedReader reader = Files.newBufferedReader(Paths.get(filePath));
        return Arrays.stream(reader.readLine().split(",")).map(Integer::valueOf).collect(Collectors.toList());
    }

    private List<Boolean> readRandomBoolFile(String filePath) throws IOException{
        BufferedReader reader = Files.newBufferedReader(Paths.get(filePath));
        return Arrays.stream(reader.readLine().split(",")).map(Boolean::getBoolean).collect(Collectors.toList());
    }

    private void generateRandomIntFile(Random valueSource, String filePath, int valuesToGenerate, int min, int cap) throws IOException {
        Path file = Paths.get(filePath);

        if(Files.exists(file))
            Files.delete(file);

        Writer writer = Files.newBufferedWriter(file);
        writer.write(valueSource.ints(valuesToGenerate, min, cap).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        writer.close();
    }

    /**
     * Get the next account from the mayority group
     */
    public Integer nextMayorityAccount(){
        if(useRandom)
            return random.nextInt(mayorityCap)+minorityCap;

        return may.next()+minorityCap;
    }

    /**
     *Get the next account from the minority group
     */
    public Integer nextMinorityAccount(){
        if(useRandom)
            return random.nextInt(minorityCap);

        return min.next();
    }

    /**
     *Get the next token contract selection
     */
    public Integer nextTokenContract(){
        if(useRandom)
            return random.nextInt(5);

        return trxSel.next();
    }

    /**
     * Get the next transaction amount
     */
    public Integer nextTrxAmount(){
        if(useRandom)
            return random.nextInt(trxAmountCap)+1;

        return trxAmt.next();
    }

    /**
     * Get the next type of transfer,
     * @return true: Normal coin transfer, false: token transfer
     */
    public Boolean nextTransferType(){
        if(useRandom)
            return random.nextBoolean();

        return trxType.next();
    }

    /**
     * Get the next coinbase index
     */
    public  Integer nextCoinbase(){
        if(useRandom)
            return random.nextInt(coinbaseCap);

        if(!coinbaseSel.hasNext()){
            coinbaseSel = coinbaseSrc.iterator();
        }

        return coinbaseSel.next();
    }


    //Functions used for unit-testing, may not have any real purpose for actual code
    public int getMayorityAccountsLength(){
        return this.mayorityAccountsSrc.size();
    }

    public int getMinorityAccountsLength(){
        return this.minorityAccountsSrc.size();
    }
    public int getTokenContractsLength(){
        return this.trxSelectionSrc.size();
    }
    public int getTrxAmountLength(){
        return this.trxAmountSrc.size();
    }
    public int getTransferTypeLength(){
        return this.trxTypeSrc.size();
    }
    public  int getCoinbaseLength(){
        return this.coinbaseSrc.size();
    }
}

