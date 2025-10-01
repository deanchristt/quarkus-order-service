package com.training.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.training.model.entity.OrderItem;

import java.util.List;

public class CreateOrderDto {

    private String email;
    private List<OrderItem> items;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
