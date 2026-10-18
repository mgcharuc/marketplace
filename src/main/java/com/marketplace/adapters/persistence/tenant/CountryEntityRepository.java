package com.marketplace.adapters.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

interface CountryEntityRepository extends JpaRepository<CountryEntity, String> {}
