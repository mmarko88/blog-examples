package unittests;

public class Util {
    private Util() { }

    public static void printEndTestWithInfo(Class<?> aClass) {
        System.out.println("end "+ Thread.currentThread() + " Class:" + aClass.getSimpleName() + " Method:" + getMethodNameFromStackTrace(3));
    }

    public static String getMethodNameFromStackTrace(int depth) {
        return Thread.currentThread().getStackTrace()[depth].getMethodName();
    }

    public static void printStartTestWithInfo(Class<? > aClass) {
        System.out.println("running "+ Thread.currentThread() + " Class:" + aClass.getSimpleName() + " Method:" + getMethodNameFromStackTrace(3));
    }

}
