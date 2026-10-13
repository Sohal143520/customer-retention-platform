package com.retention.platform.customer_retention_platform.repository;

import com.retention.platform.customer_retention_platform.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    @Query("select customer.customerId from Customer customer")
    List<String> findAllCustomerIds();
}
