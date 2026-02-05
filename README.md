# Custom Notes
- *API CHANGE* : I've made an idempotency feature, and as a trade off the idempotencyId as a HEADER 
  is required when posting a payment (that's the only change on the contract I've made). 
  Another possibility would be to use only the request object itself as the key. The problem would 
  be that two identical requests would be considered repeated, which might not be correct.

- I made another CreatePaymentResponse(Dto) class: one for the database, another one for the API,
  in order to avoid leaking internal data to another domain. The API one doesn't have `authorization_code`,
  so I didn't want to use the same object for both cases. Ideally this would be done for
  all the classes on the `model` package as well as the ones on the `enum` package, but for
  simplicity I'm not converting them all (only the one that changes for now).

- I think it's outside the scope of the task/assignment, but it would be on the scope of the project:
  if there's a failure when the Bank is called but the merchant didn't get the confirmation, we would
  need to handle this case. I'm partially solving this
  situation with a Saga pattern implementation: when a payment is requested to be made, it is first
  created on the DB with the status `PENDING`. After the confirmation it sets to `AUTHORIZED`, though
  it's not a proper solution - we might still end up with the case of the customer being charged
  but didn't get the confirmation. A possible solution: there could be a callback endpoint on the 
  merchant that would allow us to say that the payment was confirmed, even though it wasn't in real time.
  Or just wait for the merchant to retry, since the `idempotentID` would be the same.

- Another attempt to prevent the problem above-mentioned: I set graceful shutdown configuration 
 in order to prevent Spring from interrupting PaymentGatewayService.processPayment in-flight; 

- Since the Spring version used here is 3.1.5, I used the Resilience4j to implement a circuit
  breaker when calling the AcquiringBank. If the version was >= 3.2, I could use Spring's RestClient and
  use its built-in feature for this.

- Reducing http timeout to 5 seconds, instead of 10 for the AcquiringBankClient;

- cardNumber and cvv are currently int. I've converted them to String because:
Card numbers are 14-19 digits — overflows int (10 digits);
CVVs can have leading zeros (e.g., "012") which are lost as int;

- Since it wasn't specified, I'm not sure if the `amount` on the acquiring bank matches the amount on the PaymentGateway
with the same format, where `100` means `1.00 CURRENCY`. I'm assuming that it is the case for now.

- The results of POST Payment Response and GET Payment Response are the same, so I
  renamed GetPaymentResponse to PaymentResponse and use with both endpoints. This is only
  being done because they share the exact same properties.

- For the API, the `status` on the tables on the README are described as `Authorized` or `Declined`.
  I've added a new item for the Saga pattern implementation above-mentioned: `PENDING`, but this is not
  leaked to the merchant. To not change the API contract, I've converted `PENDING` on the database
  to `DECLINED` on the API, which is not quite correct, but I'm trying to change less the API.

## Testing the service
Assuming the service and the docker-compose are running:

### Post payment
`curl -s -X POST http://localhost:8090/payment     -H "Content-Type: application/json"     -H "Idempotency-Key: $(uuidgen)"     -d '{     
      "card_number": "2222405343248877",
      "expiry_month": 4,
      "expiry_year": 2027,   
      "currency": "USD",          
      "amount": 100,                   
      "cvv": "123"                          
    }' | jq 
`

Note: please notice the command `uuidgen`. It's valid on Linux, but I'm not sure about Mac and Windows.
If not valid, you can just replace `uuidgen` with any random string. Also notice that if you don't change
this string between requests, it will be considered the same request.

### Fetch payment
`curl -s http://localhost:8090/payment/f1fd626b-c26e-4707-9022-45c6cf6a8dc0 | jq`