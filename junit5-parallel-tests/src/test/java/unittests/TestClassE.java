package unittests;

import org.junit.jupiter.api.Test;

import static unittests.Util.printEndTestWithInfo;
import static unittests.Util.printStartTestWithInfo;

class TestClassE {

    @Test
    void test1() throws InterruptedException {
        printStartTestWithInfo(getClass());
        Thread.sleep(5000);
        printEndTestWithInfo(getClass());
    }

    @Test
    void test2() throws InterruptedException {
        printStartTestWithInfo(getClass());
        Thread.sleep(5000);
        printEndTestWithInfo(getClass());
    }

}
