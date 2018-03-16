package com.windyice.qwerty;

/**
 * Created by 32699 on 2018/3/12.
 */

public class QwertyException extends Exception {
    public QwertyException() {
        super();
    }

    public QwertyException(String message) {
        super(message);
    }
}

class QwertyCameraException extends QwertyException{
    public QwertyCameraException() {
        super();
    }

    public QwertyCameraException(String message) {
        super(message);
    }
}
