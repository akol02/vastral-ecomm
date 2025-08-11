// FILE: src/main/java/com/zosh/controller/OrderController.java

package com.zosh.controller;

import com.razorpay.RazorpayException;
import com.stripe.exception.StripeException;
import com.zosh.domain.PaymentMethod;
import com.zosh.exception.OrderException;
import com.zosh.exception.SellerException;
import com.zosh.exception.UserException;
import com.zosh.model.*;
import com.zosh.repository.PaymentOrderRepository;
import com.zosh.response.PaymentLinkResponse;
import com.zosh.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
	
	private final OrderService orderService;
	private final UserService userService;
	private final OrderItemService orderItemService;
	private final CartService cartService;
	private final PaymentService paymentService;
	private final PaymentOrderRepository paymentOrderRepository;
	private final SellerReportService sellerReportService;
	private final SellerService sellerService;

    @Value("${razorpay.api.key}")
    private String razorpayApiKey;

	// --- MODIFICATION START ---
    // The entire createOrderHandler method is replaced to support the Razorpay modal.
	@PostMapping()
	public ResponseEntity<?> createOrderHandler(
			@RequestBody Address spippingAddress,
			@RequestParam PaymentMethod paymentMethod,
			@RequestHeader("Authorization")String jwt)
            throws UserException, RazorpayException, StripeException {
		
		User user = userService.findUserProfileByJwt(jwt);
        Cart cart = cartService.findUserCart(user);
        Set<Order> orders = orderService.createOrder(user, spippingAddress, cart);
        PaymentOrder paymentOrder = paymentService.createOrder(user, orders);
        paymentOrderRepository.save(paymentOrder);

        if (paymentMethod.equals(PaymentMethod.RAZORPAY)) {
        	if (paymentOrder.getAmount() <= 0) {
                // If amount is 0 or less, we can't create a payment.
                // You might want to handle this case, e.g., mark the order as paid if it's a free item.
                // For now, we return an error.
                return new ResponseEntity<>("Invalid order amount. Must be greater than 0.", HttpStatus.BAD_REQUEST);
            }

            try {
                com.razorpay.Order razorpayOrder = paymentService.createRazorpayOrder(
                        paymentOrder.getAmount(), paymentOrder.getId().toString());

                Map<String, Object> response = new HashMap<>();
                response.put("razorpayOrderId", razorpayOrder.get("id"));
                response.put("amount", razorpayOrder.get("amount"));
                response.put("currency", razorpayOrder.get("currency"));
                response.put("internalPaymentOrderId", paymentOrder.getId());
                response.put("razorpayKey", razorpayApiKey);

                paymentOrder.setPaymentLinkId(razorpayOrder.get("id"));
                paymentOrderRepository.save(paymentOrder);

                System.out.println("Successfully created Razorpay order. Sending details to frontend.");
                return new ResponseEntity<>(response, HttpStatus.OK);
            } catch (RazorpayException e) {
                // Add detailed logging to see the exact error from Razorpay.
                System.err.println("RazorpayException in OrderController: " + e.getMessage());
                // This re-throws the exception to be caught by your global handler, which is good.
                throw e;
            }

        } else if (paymentMethod.equals(PaymentMethod.STRIPE)) {
            String paymentUrl = paymentService.createStripePaymentLink(user,
                    paymentOrder.getAmount(),
                    paymentOrder.getId());
            PaymentLinkResponse res = new PaymentLinkResponse();
            res.setPayment_link_url(paymentUrl);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }

        return new ResponseEntity<>("Unsupported Payment Method", HttpStatus.BAD_REQUEST);
	}
    // --- MODIFICATION END ---
	
	@GetMapping("/user")
	public ResponseEntity< List<Order>> usersOrderHistoryHandler(
			@RequestHeader("Authorization")
	String jwt) throws UserException{
		
		User user=userService.findUserProfileByJwt(jwt);
		List<Order> orders=orderService.usersOrderHistory(user.getId());
		return new ResponseEntity<>(orders,HttpStatus.ACCEPTED);
	}
	
	@GetMapping("/{orderId}")
	public ResponseEntity< Order> getOrderById(@PathVariable Long orderId, @RequestHeader("Authorization")
	String jwt) throws OrderException, UserException{
		
		User user = userService.findUserProfileByJwt(jwt);
		Order orders=orderService.findOrderById(orderId);
		return new ResponseEntity<>(orders,HttpStatus.ACCEPTED);
	}

	@GetMapping("/item/{orderItemId}")
	public ResponseEntity<OrderItem> getOrderItemById(
			@PathVariable Long orderItemId, @RequestHeader("Authorization")
	String jwt) throws Exception {
		System.out.println("------- controller ");
		User user = userService.findUserProfileByJwt(jwt);
		OrderItem orderItem=orderItemService.getOrderItemById(orderItemId);
		return new ResponseEntity<>(orderItem,HttpStatus.ACCEPTED);
	}

	@PutMapping("/{orderId}/cancel")
	public ResponseEntity<Order> cancelOrder(
			@PathVariable Long orderId,
			@RequestHeader("Authorization") String jwt
	) throws UserException, OrderException, SellerException {
		User user=userService.findUserProfileByJwt(jwt);
		Order order=orderService.cancelOrder(orderId,user);

		Seller seller= sellerService.getSellerById(order.getSellerId());
		SellerReport report=sellerReportService.getSellerReport(seller);

		report.setCanceledOrders(report.getCanceledOrders()+1);
		report.setTotalRefunds(report.getTotalRefunds()+order.getTotalSellingPrice());
		sellerReportService.updateSellerReport(report);

		return ResponseEntity.ok(order);
	}

}