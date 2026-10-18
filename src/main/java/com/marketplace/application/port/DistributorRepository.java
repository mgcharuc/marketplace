package com.marketplace.application.port;

public interface DistributorRepository {

    boolean exists(String tenantId, String distributorId);

    void add(String tenantId, DistributorSeed distributor);
}
