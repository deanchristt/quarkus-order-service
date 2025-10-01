package com.training.controller;

import com.training.model.dto.CreateOrderDto;
import com.training.model.entity.Order;
import com.training.service.OrderService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/order")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderController {

    @Inject
    OrderService orderService;

    // STEP 1: Buat order baru (customerName + list produk)
    @POST
    public Response createOrder(CreateOrderDto dto) {
        Order order = orderService.createOrder(dto);
        return Response.ok(order).build();
    }

    // STEP 2: Input alamat pengiriman
    @PUT
    @Path("/address")
    public Response updateAddress(Order order,
                                  @QueryParam("address") String address) {
        Order updated = orderService.updateShippingAddress(order, address);
        return Response.ok(updated).build();
    }

    // STEP 3: Konfirmasi PIN pembayaran
    @PUT
    @Path("/payment")
    public Response confirmPayment(Order order) {
        Order updated = orderService.confirmPayment(order);
        return Response.ok(updated).build();
    }

    // STEP 4: Proses pembayaran
    @POST
    @Path("/process-payment")
    public Response processPayment(Order order,
                                   @QueryParam("pin") String pin) {
        Order updated = orderService.processPayment(order, pin);
        return Response.ok(updated).build();
    }

    // STEP 5: Kirim order
    @POST
    @Path("/ship")
    public Response shipOrder(Order order) {
        Order updated = orderService.shipOrder(order);
        return Response.ok(updated).build();
    }

    // STEP 6: Selesaikan order
    @POST
    @Path("/complete")
    public Response completeOrder(Order order) {
        Order updated = orderService.completeOrder(order);
        return Response.ok(updated).build();
    }
}
