package util;

import java.util.regex.Pattern;

public final class Utility {

    public static boolean isPositiveInteger(String input){
        return Pattern.compile("^\\d+$").matcher(input).matches();
    }

    public static boolean checkIntegerInBounds(int low, int high, String str){
        return Pattern.compile("^[" + low + "-" + high + "]$").matcher(str).matches();
    }

    public static String concatinateStringFromArray(int low, int high, String[] arr){
        StringBuilder sb = new StringBuilder();
        for (int i = low; i < high; i++) {
            sb.append(arr[i]).append(" ");
        }

        return sb.toString();
    }

    public static void clearLine(){
        System.out.print("\r" + "\s".repeat(50) + "\r");
    }

}
