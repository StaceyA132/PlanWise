package com.planwise.web;

/**
 * Something doesn't exist, or belongs to another user. Both return 404 so one user
 * can't discover another user's plan ids.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
