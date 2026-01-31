// PayinExtendedOrderResponseDTO.java
package com.example.payment_services.dto.payin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PayinExtendedOrderResponseDTO {
    @JsonProperty("cf_order_id")
    private String cfOrderId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("order_amount")
    private Double orderAmount;

    @JsonProperty("order_currency")
    private String orderCurrency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("charges")
    private Charges charges;

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    @JsonProperty("shipping_address")
    private Address shippingAddress;

    @JsonProperty("billing_address")
    private Address billingAddress;

    @JsonProperty("cart")
    private Cart cart;

    @JsonProperty("offer")
    private Offer offer;

    @Data
    public static class Charges {
        @JsonProperty("shipping_charges")
        private Double shippingCharges;

        @JsonProperty("cod_handling_charges")
        private Double codHandlingCharges;
    }

    @Data
    public static class CustomerDetails {
        @JsonProperty("customer_id")
        private String customerId;

        @JsonProperty("customer_name")
        private String customerName;

        @JsonProperty("customer_email")
        private String customerEmail;

        @JsonProperty("customer_phone")
        private String customerPhone;

        @JsonProperty("customer_uid")
        private String customerUid;
    }

    @Data
    public static class Address {
        private String name;

        @JsonProperty("address_line_one")
        private String addressLineOne;

        @JsonProperty("address_line_two")
        private String addressLineTwo;

        private String country;

        @JsonProperty("country_code")
        private String countryCode;

        private String state;

        @JsonProperty("state_code")
        private String stateCode;

        private String city;

        @JsonProperty("pin_code")
        private String pinCode;

        private String phone;
        private String email;
    }

    @Data
    public static class Cart {
        private String name;
        private List<CartItem> items;
    }

    @Data
    public static class CartItem {
        @JsonProperty("item_id")
        private String itemId;

        @JsonProperty("item_name")
        private String itemName;

        @JsonProperty("item_description")
        private String itemDescription;

        @JsonProperty("item_tags")
        private List<String> itemTags;

        @JsonProperty("item_details_url")
        private String itemDetailsUrl;

        @JsonProperty("item_image_url")
        private String itemImageUrl;

        @JsonProperty("item_original_unit_price")
        private String itemOriginalUnitPrice;

        @JsonProperty("item_discounted_unit_price")
        private String itemDiscountedUnitPrice;

        @JsonProperty("item_quantity")
        private Integer itemQuantity;

        @JsonProperty("item_currency")
        private String itemCurrency;
    }

    @Data
    public static class Offer {
        @JsonProperty("offer_id")
        private String offerId;

        @JsonProperty("offer_status")
        private String offerStatus;

        @JsonProperty("offer_meta")
        private Map<String, String> offerMeta;
    }
}