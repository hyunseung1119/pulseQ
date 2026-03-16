package com.pulseq.core.exception

sealed class PulseQException(
    val errorType: String,
    override val message: String,
    val status: Int
) : RuntimeException(message)

class TenantNotFoundException(detail: String = "Tenant not found") :
    PulseQException("tenant-not-found", detail, 404)

class TenantAlreadyExistsException(email: String) :
    PulseQException("tenant-already-exists", "Tenant with email '$email' already exists", 409)

class InvalidCredentialsException :
    PulseQException("invalid-credentials", "Invalid email or password", 401)

class InvalidApiKeyException :
    PulseQException("invalid-api-key", "Invalid or missing API key", 401)

class EventNotFoundException(eventId: String) :
    PulseQException("event-not-found", "Event with id '$eventId' not found", 404)

class EventNotActiveException(eventId: String) :
    PulseQException("event-not-active", "Event '$eventId' is not currently active", 400)

class QueueFullException(eventId: String, maxCapacity: Int) :
    PulseQException("queue-full", "Event '$eventId' has reached maximum capacity of $maxCapacity", 429)

class AlreadyInQueueException(userId: String, existingTicket: String) :
    PulseQException("already-in-queue", "User '$userId' is already in the queue", 409)

class RateLimitExceededException :
    PulseQException("rate-limit-exceeded", "Rate limit exceeded", 429)
