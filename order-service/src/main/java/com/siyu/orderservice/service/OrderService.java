package com.siyu.orderservice.service;

import com.netflix.discovery.converters.Auto;
import com.siyu.orderservice.dto.InventoryResponse;
import com.siyu.orderservice.dto.OrderLineItemsDto;
import com.siyu.orderservice.dto.OrderRequest;
import com.siyu.orderservice.model.Order;
import com.siyu.orderservice.model.OrderLineItems;
import com.siyu.orderservice.repository.OrderRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;
    @Autowired
    private ObservationRegistry observationRegistry;
    
    public String placeOrder(OrderRequest orderRequest){
        Order order=new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemList(orderLineItems);
        List<String> skuCodes=order.getOrderLineItemList().stream().map(OrderLineItems::getSkuCode).toList();

        Observation inventoryServiceObservation = Observation.createNotStarted("inventory-service-lookup",
                this.observationRegistry);
        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");

        return inventoryServiceObservation.observe(()->{
            //call inventory service and place order if product is in
            InventoryResponse[] inventoryResponses= webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode",skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();
            boolean allProductsInStock= Arrays.stream(inventoryResponses).allMatch(InventoryResponse::isInStock);

            if(allProductsInStock){
                orderRepository.save(order);
                return "Order Placed Successfully";
            }else {
                throw new IllegalArgumentException("Product is not in stock");
            }
        });
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems=new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
