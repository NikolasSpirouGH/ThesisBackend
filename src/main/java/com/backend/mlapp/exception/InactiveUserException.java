package com.backend.mlapp.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException(String message)
    {
        super(message);
    }
}
