package com.example.data.mock

import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.*

object MockDataGenerator {

    fun getInitialFoodCategories(): List<MenuCategory> = listOf(
        MenuCategory("cat_burgers", "Burgers & Sandwiches", 1, true, BusinessType.FOOD),
        MenuCategory("cat_pizza", "Pizza & Pasta", 2, true, BusinessType.FOOD),
        MenuCategory("cat_sides", "Sides & Appetizers", 3, true, BusinessType.FOOD),
        MenuCategory("cat_beverages", "Beverages & Desserts", 4, true, BusinessType.FOOD)
    )

    fun getInitialFoodItems(): List<MenuItem> = listOf(
        // Burgers & Sandwiches
        MenuItem(
            id = "item_b1",
            categoryId = "cat_burgers",
            name = "Delivio Ultimate Smash Burger",
            description = "Double Angus smashed beef patties, cheddar cheese, caramelized onions, house truffle mayo on toasted brioche bun",
            price = 8.99,
            iconEmoji = "🍔",
            prepTimeMinutes = 15,
            tags = listOf("Best Seller", "Chef Special"),
            sizes = listOf("Single", "Double", "Triple"),
            modifiers = listOf(
                MenuItemModifier("mod_cheese", "Extra Melted Cheddar", 1.25),
                MenuItemModifier("mod_bacon", "Crispy Bacon Strips", 1.75),
                MenuItemModifier("mod_jalapeno", "Pickled Jalapeños", 0.75)
            )
        ),
        MenuItem(
            id = "item_b2",
            categoryId = "cat_burgers",
            name = "Crispy Nashville Hot Chicken Burger",
            description = "Buttermilk fried chicken breast drenched in Nashville cayenne glaze, spicy coleslaw, pickles, and garlic sauce",
            price = 7.99,
            iconEmoji = "🍗",
            prepTimeMinutes = 12,
            tags = listOf("Spicy", "Best Seller"),
            modifiers = listOf(
                MenuItemModifier("mod_heat_mild", "Extra Hot Pepper Dip", 0.99),
                MenuItemModifier("mod_cheese_slice", "Pepperjack Slice", 1.00)
            )
        ),
        MenuItem(
            id = "item_b3",
            categoryId = "cat_burgers",
            name = "Truffle Mushroom Swiss Burger",
            description = "Char-grilled beef patty topped with sautéed cremini mushrooms, Swiss emmental, and black truffle aioli",
            price = 9.49,
            iconEmoji = "🍄",
            prepTimeMinutes = 18,
            tags = listOf("Chef Special")
        ),
        MenuItem(
            id = "item_b4",
            categoryId = "cat_burgers",
            name = "Smoky BBQ Pulled Pork Sandwich",
            description = "Slow-cooked hickory smoked pulled pork shoulder, tangy barbecue glaze, and creamy apple slaw",
            price = 8.25,
            iconEmoji = "🥪",
            prepTimeMinutes = 10
        ),
        MenuItem(
            id = "item_b5",
            categoryId = "cat_burgers",
            name = "Green Goddess Vegan Burger",
            description = "Beyond Meat plant-based patty, mashed avocado, arugula, tomato relish, and vegan cashew herb sauce",
            price = 9.99,
            iconEmoji = "🥑",
            prepTimeMinutes = 15,
            tags = listOf("Vegetarian")
        ),
        MenuItem(
            id = "item_b6",
            categoryId = "cat_burgers",
            name = "Classic Cheeseburger Deluxe",
            description = "Quarter pound pure beef patty, American cheddar, crisp lettuce, tomato, sliced red onions, and signature burger sauce",
            price = 6.49,
            iconEmoji = "🍔",
            prepTimeMinutes = 10
        ),

        // Pizza & Pasta
        MenuItem(
            id = "item_p1",
            categoryId = "cat_pizza",
            name = "Artisan Pepperoni & Hot Honey Pizza",
            description = "Slow-fermented sourdough crust, San Marzano tomato sauce, fresh mozzarella, spicy pepperoni crisps, and chili hot honey drizzle",
            price = 14.50,
            iconEmoji = "🍕",
            prepTimeMinutes = 20,
            tags = listOf("Best Seller", "Spicy"),
            sizes = listOf("Medium (10\")", "Large (14\")")
        ),
        MenuItem(
            id = "item_p2",
            categoryId = "cat_pizza",
            name = "Creamy Truffle Carbonara Fettuccine",
            description = "Al dente fettuccine tossed in egg yolk cream, crispy pancetta, cracked tellicherry pepper, and aged parmesan reggiano",
            price = 11.80,
            iconEmoji = "🍝",
            prepTimeMinutes = 15,
            tags = listOf("Chef Special"),
            modifiers = listOf(
                MenuItemModifier("mod_p_truffle", "Extra Shaved Truffle", 2.50),
                MenuItemModifier("mod_p_garlic", "Garlic Bread Slice", 1.50)
            )
        ),
        MenuItem(
            id = "item_p3",
            categoryId = "cat_pizza",
            name = "Margherita D.O.P. Woodfired Pizza",
            description = "Buffalo mozzarella, San Marzano tomato sauce, fresh fragrant basil leaves, and extra virgin olive oil drizzle",
            price = 12.00,
            iconEmoji = "🍕",
            prepTimeMinutes = 15,
            tags = listOf("Vegetarian")
        ),
        MenuItem(
            id = "item_p4",
            categoryId = "cat_pizza",
            name = "Spicy Arrabiata Penne with Burrata",
            description = "Fiery garlic chili tomato sugo, artisanal bronze-cut penne, topped with a whole fresh molten burrata ball",
            price = 10.99,
            iconEmoji = "🍝",
            prepTimeMinutes = 14,
            tags = listOf("Vegetarian", "Spicy")
        ),
        MenuItem(
            id = "item_p5",
            categoryId = "cat_pizza",
            name = "Four Cheese & Garlic White Pizza",
            description = "Mozzarella, Gorgonzola, Ricotta, and Pecorino Romano with confit garlic puree and fresh rosemary",
            price = 13.99,
            iconEmoji = "🧀",
            prepTimeMinutes = 18
        ),
        MenuItem(
            id = "item_p6",
            categoryId = "cat_pizza",
            name = "Slow-Braised Beef Bolognese Lasagna",
            description = "Layered handmade pasta sheets, rich 6-hour simmered ragu bolognese, creamy bechamel, and bubbling mozzarella crust",
            price = 12.50,
            iconEmoji = "🍲",
            prepTimeMinutes = 20
        ),

        // Sides & Appetizers
        MenuItem(
            id = "item_s1",
            categoryId = "cat_sides",
            name = "Parmesan Truffle Shoestring Fries",
            description = "Golden crisp skinny potatoes tossed in white truffle oil, grated parmesan, parsley, with roasted garlic garlic aioli",
            price = 4.99,
            iconEmoji = "🍟",
            prepTimeMinutes = 8,
            tags = listOf("Best Seller")
        ),
        MenuItem(
            id = "item_s2",
            categoryId = "cat_sides",
            name = "Crispy Mozzarella Sticks (6 pcs)",
            description = "Stretchy herb-crusted whole milk mozzarella batons served with warm spicy marinara dip",
            price = 5.25,
            iconEmoji = "🧀",
            prepTimeMinutes = 8
        ),
        MenuItem(
            id = "item_s3",
            categoryId = "cat_sides",
            name = "Buffalo Wings with Blue Cheese",
            description = "Jumbo crisp chicken wings tossed in tangy cayenne butter glaze, celery sticks, and house chunky blue cheese dip",
            price = 7.99,
            iconEmoji = "🍗",
            prepTimeMinutes = 12,
            tags = listOf("Spicy")
        ),
        MenuItem(
            id = "item_s4",
            categoryId = "cat_sides",
            name = "Beer-Battered Onion Rings",
            description = "Thick cut sweet Spanish onions in golden craft IPA batter served with chipotle ranch",
            price = 4.50,
            iconEmoji = "🧅",
            prepTimeMinutes = 8
        ),
        MenuItem(
            id = "item_s5",
            categoryId = "cat_sides",
            name = "Loaded Bacon Queso Nachos",
            description = "Corn tortilla chips layered with hot molten queso cheese, crispy smoked bacon bits, jalapeños, and pico de gallo",
            price = 6.99,
            iconEmoji = "🧀",
            prepTimeMinutes = 10
        ),
        MenuItem(
            id = "item_s6",
            categoryId = "cat_sides",
            name = "Fresh Caesar Salad with Grilled Chicken",
            description = "Romaine hearts, shaved parmesan, garlic herb croutons, and grilled chicken breast in creamy anchovy dressing",
            price = 6.75,
            iconEmoji = "🥗",
            prepTimeMinutes = 10
        ),

        // Beverages & Desserts
        MenuItem(
            id = "item_d1",
            categoryId = "cat_beverages",
            name = "Iced Salted Caramel Cream Cold Brew",
            description = "16-hour slow steeped Arabica cold brew coffee topped with thick aerated salted caramel sweet cold foam",
            price = 4.25,
            iconEmoji = "☕",
            prepTimeMinutes = 5,
            tags = listOf("Best Seller"),
            sizes = listOf("Medium 16oz", "Large 24oz")
        ),
        MenuItem(
            id = "item_d2",
            categoryId = "cat_beverages",
            name = "Fresh Passionfruit Mango Iced Tea",
            description = "Jasmine green tea infused with crushed tropical passionfruit and fresh Alphonso mango pulp",
            price = 3.99,
            iconEmoji = "🧋",
            prepTimeMinutes = 4
        ),
        MenuItem(
            id = "item_d3",
            categoryId = "cat_beverages",
            name = "Molten Chocolate Lava Cake",
            description = "Warm dark Valrhona chocolate cake with oozing liquid center served with vanilla bean cream",
            price = 5.99,
            iconEmoji = "🍰",
            prepTimeMinutes = 10,
            tags = listOf("Chef Special")
        ),
        MenuItem(
            id = "item_d4",
            categoryId = "cat_beverages",
            name = "Classic New York Strawberry Cheesecake",
            description = "Velvety baked cream cheese slice on graham cracker crust with sweet macerated strawberry compote",
            price = 5.50,
            iconEmoji = "🍰",
            prepTimeMinutes = 4
        ),
        MenuItem(
            id = "item_d5",
            categoryId = "cat_beverages",
            name = "Fresh Lemon Calamansi Mint Cooler",
            description = "Sparkling zesty lemonade with muddled Philippine calamansi citrus and fresh garden mint leaves",
            price = 3.50,
            iconEmoji = "🍋",
            prepTimeMinutes = 3
        ),
        MenuItem(
            id = "item_d6",
            categoryId = "cat_beverages",
            name = "Craft Root Beer Float",
            description = "Old-fashioned cane sugar root beer poured over two generous scoops of rich Madagascar vanilla ice cream",
            price = 4.50,
            iconEmoji = "🍨",
            prepTimeMinutes = 5
        )
    )

    fun getInitialConvenienceCategories(): List<MenuCategory> = listOf(
        MenuCategory("cat_c_drinks", "Cold Drinks & Dairy", 1, true, BusinessType.CONVENIENCE),
        MenuCategory("cat_c_snacks", "Snacks & Sweets", 2, true, BusinessType.CONVENIENCE),
        MenuCategory("cat_c_instant", "Instant Food & Pantry", 3, true, BusinessType.CONVENIENCE),
        MenuCategory("cat_c_personal", "Personal Care & Essentials", 4, true, BusinessType.CONVENIENCE)
    )

    fun getInitialConvenienceItems(): List<MenuItem> = listOf(
        MenuItem(
            id = "conv_1",
            categoryId = "cat_c_drinks",
            name = "Delivio Fresh Whole Milk 1L",
            description = "100% pasteurized pure full cream dairy milk carton",
            price = 2.49,
            iconEmoji = "🥛",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-MLK-1001",
            barcode = "4800012345678",
            unit = "1000ml",
            stockCount = 34,
            lowStockAlertThreshold = 8
        ),
        MenuItem(
            id = "conv_2",
            categoryId = "cat_c_drinks",
            name = "Sparkling Mineral Water 500ml",
            description = "Naturally carbonated crisp mountain spring water",
            price = 1.20,
            iconEmoji = "💧",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-WTR-2002",
            barcode = "4800012345679",
            unit = "500ml",
            stockCount = 48,
            lowStockAlertThreshold = 10
        ),
        MenuItem(
            id = "conv_3",
            categoryId = "cat_c_drinks",
            name = "Electro-Hydrate Sports Drink Citrus",
            description = "Rapid electrolyte hydration isotonic beverage",
            price = 1.85,
            iconEmoji = "⚡",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-SPT-3003",
            barcode = "4800012345680",
            unit = "600ml",
            stockCount = 22,
            lowStockAlertThreshold = 5
        ),
        MenuItem(
            id = "conv_4",
            categoryId = "cat_c_snacks",
            name = "Kettle Cooked Sea Salt Chips",
            description = "Extra crunchy thick-cut artisan potato chips with sea salt",
            price = 2.99,
            iconEmoji = "🥔",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-CHP-4001",
            barcode = "4800098765432",
            unit = "150g bag",
            stockCount = 18,
            lowStockAlertThreshold = 6
        ),
        MenuItem(
            id = "conv_5",
            categoryId = "cat_c_snacks",
            name = "Swiss Hazelnut Milk Chocolate Bar",
            description = "Velvety Alpine milk chocolate with roasted hazelnut pieces",
            price = 3.50,
            iconEmoji = "🍫",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-CHK-4002",
            barcode = "4800098765433",
            unit = "100g",
            stockCount = 28,
            lowStockAlertThreshold = 5
        ),
        MenuItem(
            id = "conv_6",
            categoryId = "cat_c_snacks",
            name = "Trail Mix Nuts & Dried Cranberries",
            description = "Almonds, cashews, walnuts, pumpkin seeds, and sweet cranberries",
            price = 4.25,
            iconEmoji = "🥜",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-NUT-4003",
            barcode = "4800098765434",
            unit = "200g pouch",
            stockCount = 12,
            lowStockAlertThreshold = 4
        ),
        MenuItem(
            id = "conv_7",
            categoryId = "cat_c_instant",
            name = "Spicy Black Garlic Ramen Bowl",
            description = "Instant tonkotsu broth ramen with springy noodles and chashu oil",
            price = 2.10,
            iconEmoji = "🍜",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-RMN-5001",
            barcode = "4800055512341",
            unit = "1 bowl (120g)",
            stockCount = 40,
            lowStockAlertThreshold = 10
        ),
        MenuItem(
            id = "conv_8",
            categoryId = "cat_c_instant",
            name = "Whole Grain Sliced Sandwich Bread",
            description = "Freshly baked soft wheat sandwich bread loaf",
            price = 1.95,
            iconEmoji = "🍞",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-BRD-5002",
            barcode = "4800055512342",
            unit = "450g loaf",
            stockCount = 15,
            lowStockAlertThreshold = 4
        ),
        MenuItem(
            id = "conv_9",
            categoryId = "cat_c_instant",
            name = "Organic Grade A Eggs (12 pack)",
            description = "Farm-fresh cage-free brown eggs carton",
            price = 3.99,
            iconEmoji = "🥚",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-EGG-5003",
            barcode = "4800055512343",
            unit = "Dozen",
            stockCount = 8,
            lowStockAlertThreshold = 5
        ),
        MenuItem(
            id = "conv_10",
            categoryId = "cat_c_personal",
            name = "Antibacterial Hand Sanitizer Gel",
            description = "75% ethyl alcohol with aloe vera soothing extract",
            price = 2.25,
            iconEmoji = "🧴",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-SNT-6001",
            barcode = "4800077799911",
            unit = "250ml pump",
            stockCount = 30,
            lowStockAlertThreshold = 5
        ),
        MenuItem(
            id = "conv_11",
            categoryId = "cat_c_personal",
            name = "Double Velvet 3-Ply Bath Tissue (4 rolls)",
            description = "Ultra-absorbent soft embossed tissue rolls pack",
            price = 3.80,
            iconEmoji = "🧻",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-TSS-6002",
            barcode = "4800077799912",
            unit = "4 pack",
            stockCount = 20,
            lowStockAlertThreshold = 4
        ),
        MenuItem(
            id = "conv_12",
            categoryId = "cat_c_personal",
            name = "Refreshing Peppermint Toothpaste 150g",
            description = "Fluoride cavity protection with natural whitening baking soda",
            price = 2.65,
            iconEmoji = "🪥",
            businessType = BusinessType.CONVENIENCE,
            sku = "SKU-TP-6003",
            barcode = "4800077799913",
            unit = "150g tube",
            stockCount = 19,
            lowStockAlertThreshold = 5
        )
    )

    fun getInitialOrders(): List<Order> {
        val now = System.currentTimeMillis()
        return listOf(
            // Order 1: New order (Placed Order)
            Order(
                id = "ord_1081",
                orderNumber = "#ORD-1081",
                customerName = "Sophia Ramirez",
                customerPhone = "+63 918 334 5567",
                deliveryAddress = AddressLocation(
                    street = "Unit 14B, Avida Towers 34th Street",
                    city = "BGC, Taguig City",
                    latitude = 14.5521,
                    longitude = 121.0535,
                    notes = "Drop at lobby concierge"
                ),
                items = listOf(
                    OrderItem(
                        id = "oi_1",
                        menuItemId = "item_b1",
                        name = "Delivio Ultimate Smash Burger",
                        quantity = 2,
                        unitPrice = 8.99,
                        selectedSize = "Double",
                        modifiers = listOf(OrderModifier("Extra Melted Cheddar", 1.25)),
                        notes = "No pickles please"
                    ),
                    OrderItem(
                        id = "oi_2",
                        menuItemId = "item_s1",
                        name = "Parmesan Truffle Shoestring Fries",
                        quantity = 1,
                        unitPrice = 4.99
                    ),
                    OrderItem(
                        id = "oi_3",
                        menuItemId = "item_d1",
                        name = "Iced Salted Caramel Cream Cold Brew",
                        quantity = 2,
                        unitPrice = 4.25,
                        selectedSize = "Medium 16oz"
                    )
                ),
                subtotal = 33.72,
                deliveryFee = 2.50,
                platformFee = 1.50,
                total = 37.72,
                paymentMethod = "Delivio Pay",
                status = OrderStatus.PLACED_ORDER,
                placedAtTimestamp = now - (2 * 60 * 1000) // 2 mins ago
            ),

            // Order 2: Preparing
            Order(
                id = "ord_1082",
                orderNumber = "#ORD-1082",
                customerName = "Marcus Vance",
                customerPhone = "+63 927 889 1122",
                deliveryAddress = AddressLocation(
                    street = "12 Cypress Grove, McKinley Hill",
                    city = "Taguig, Metro Manila",
                    latitude = 14.5350,
                    longitude = 121.0490,
                    notes = "Ring gate buzzer #12"
                ),
                items = listOf(
                    OrderItem(
                        id = "oi_4",
                        menuItemId = "item_p1",
                        name = "Artisan Pepperoni & Hot Honey Pizza",
                        quantity = 1,
                        unitPrice = 14.50,
                        selectedSize = "Large (14\")"
                    ),
                    OrderItem(
                        id = "oi_5",
                        menuItemId = "item_s3",
                        name = "Buffalo Wings with Blue Cheese",
                        quantity = 1,
                        unitPrice = 7.99,
                        notes = "Extra crispy wings"
                    )
                ),
                subtotal = 22.49,
                deliveryFee = 3.00,
                platformFee = 1.20,
                total = 26.69,
                paymentMethod = "Cash on Delivery",
                status = OrderStatus.PREPARING,
                placedAtTimestamp = now - (10 * 60 * 1000),
                prepMinutes = 20,
                prepEndTime = now + (10 * 60 * 1000) // 10 mins remaining
            ),

            // Order 3: Preparing
            Order(
                id = "ord_1083",
                orderNumber = "#ORD-1083",
                customerName = "Elena Santos",
                customerPhone = "+63 915 444 8899",
                deliveryAddress = AddressLocation(
                    street = "Tower 2, Serendra Condominium",
                    city = "Bonifacio Global City",
                    latitude = 14.5510,
                    longitude = 121.0520,
                    notes = "Leave on table outside door"
                ),
                items = listOf(
                    OrderItem(
                        id = "oi_6",
                        menuItemId = "item_b2",
                        name = "Crispy Nashville Hot Chicken Burger",
                        quantity = 1,
                        unitPrice = 7.99,
                        modifiers = listOf(OrderModifier("Pepperjack Slice", 1.00))
                    ),
                    OrderItem(
                        id = "oi_7",
                        menuItemId = "item_d5",
                        name = "Fresh Lemon Calamansi Mint Cooler",
                        quantity = 1,
                        unitPrice = 3.50
                    )
                ),
                subtotal = 12.49,
                deliveryFee = 2.50,
                platformFee = 1.00,
                total = 15.99,
                paymentMethod = "Credit Card",
                status = OrderStatus.PREPARING,
                placedAtTimestamp = now - (15 * 60 * 1000),
                prepMinutes = 15,
                prepEndTime = now + (3 * 60 * 1000)
            ),

            // Order 4: Ready (Can request rider)
            Order(
                id = "ord_1084",
                orderNumber = "#ORD-1084",
                customerName = "Gabriel Torres",
                customerPhone = "+63 908 776 5432",
                deliveryAddress = AddressLocation(
                    street = "8F Cyberpark Tower 1, Cubao",
                    city = "Quezon City",
                    latitude = 14.6200,
                    longitude = 121.0550,
                    notes = "Call when arriving at reception"
                ),
                items = listOf(
                    OrderItem(
                        id = "oi_8",
                        menuItemId = "item_p2",
                        name = "Creamy Truffle Carbonara Fettuccine",
                        quantity = 2,
                        unitPrice = 11.80,
                        modifiers = listOf(OrderModifier("Garlic Bread Slice", 1.50))
                    ),
                    OrderItem(
                        id = "oi_9",
                        menuItemId = "item_d3",
                        name = "Molten Chocolate Lava Cake",
                        quantity = 1,
                        unitPrice = 5.99
                    )
                ),
                subtotal = 32.59,
                deliveryFee = 3.50,
                platformFee = 1.50,
                total = 37.59,
                paymentMethod = "Delivio Pay",
                status = OrderStatus.READY,
                placedAtTimestamp = now - (25 * 60 * 1000)
            ),

            // Order 5: Rider on the way
            Order(
                id = "ord_1085",
                orderNumber = "#ORD-1085",
                customerName = "Chloe Reyes",
                customerPhone = "+63 919 123 9988",
                deliveryAddress = AddressLocation(
                    street = "55 Acacia Avenue, Dasmariñas Village",
                    city = "Makati City",
                    latitude = 14.5420,
                    longitude = 121.0310
                ),
                items = listOf(
                    OrderItem(
                        id = "oi_10",
                        menuItemId = "item_b3",
                        name = "Truffle Mushroom Swiss Burger",
                        quantity = 2,
                        unitPrice = 9.49
                    )
                ),
                subtotal = 18.98,
                deliveryFee = 2.50,
                platformFee = 1.10,
                total = 22.58,
                paymentMethod = "Delivio Pay",
                status = OrderStatus.RIDER_ON_THE_WAY,
                placedAtTimestamp = now - (35 * 60 * 1000),
                riderRequest = RiderRequest(
                    id = "rr_mock_1",
                    orderId = "ord_1085",
                    status = "Rider is on the way",
                    rider = Rider(
                        id = "rdr_carlos",
                        name = "Carlos Mendoza",
                        phone = "+63 920 555 7788",
                        vehiclePlate = "HONDA BEAT - ABC 4812",
                        rating = 4.9,
                        etaMinutes = 7,
                        avatarEmoji = "🛵"
                    ),
                    pickupAddress = AddressLocation("88 Rizal Ave", "BGC Taguig", 14.5492, 121.0503),
                    dropoffAddress = AddressLocation("55 Acacia Ave", "Makati City", 14.5420, 121.0310),
                    deliveryFee = 3.50,
                    progressPercent = 0.65f
                )
            ),

            // Order 6: Delivered (Completed)
            Order(
                id = "ord_1079",
                orderNumber = "#ORD-1079",
                customerName = "Liam Gallagher",
                customerPhone = "+63 917 222 3344",
                deliveryAddress = AddressLocation("High Street South Block", "BGC, Taguig", 14.5505, 121.0515),
                items = listOf(
                    OrderItem("oi_11", "item_b1", "Delivio Ultimate Smash Burger", 1, 8.99)
                ),
                subtotal = 8.99,
                deliveryFee = 2.00,
                platformFee = 1.00,
                total = 11.99,
                paymentMethod = "Cash on Delivery",
                status = OrderStatus.DELIVERED,
                placedAtTimestamp = now - (90 * 60 * 1000)
            ),

            // Order 7: Delivered (Completed)
            Order(
                id = "ord_1080",
                orderNumber = "#ORD-1080",
                customerName = "Beatrice Lim",
                customerPhone = "+63 922 445 6677",
                deliveryAddress = AddressLocation("Forbes Town Road Tower 1", "BGC, Taguig", 14.5480, 121.0470),
                items = listOf(
                    OrderItem("oi_12", "item_p1", "Artisan Pepperoni & Hot Honey Pizza", 1, 14.50),
                    OrderItem("oi_13", "item_d1", "Iced Salted Caramel Cream Cold Brew", 1, 4.25)
                ),
                subtotal = 18.75,
                deliveryFee = 2.50,
                platformFee = 1.25,
                total = 22.50,
                paymentMethod = "Credit Card",
                status = OrderStatus.DELIVERED,
                placedAtTimestamp = now - (120 * 60 * 1000)
            ),

            // Order 8: Cancelled
            Order(
                id = "ord_1075",
                orderNumber = "#ORD-1075",
                customerName = "Alexander Cruz",
                customerPhone = "+63 933 111 2233",
                deliveryAddress = AddressLocation("Market! Market! C5 Road", "Taguig", 14.5490, 121.0570),
                items = listOf(
                    OrderItem("oi_14", "item_s3", "Buffalo Wings with Blue Cheese", 2, 7.99)
                ),
                subtotal = 15.98,
                deliveryFee = 2.50,
                platformFee = 1.00,
                total = 19.48,
                paymentMethod = "Delivio Pay",
                status = OrderStatus.CANCELLED,
                cancelReason = "Customer requested cancellation before prep",
                placedAtTimestamp = now - (180 * 60 * 1000)
            )
        )
    }

    fun getInitialPayouts(): List<Payout> = listOf(
        Payout(
            id = "po_1",
            reference = "PAY-20261012-001",
            amount = 450.00,
            status = "Completed",
            date = "Oct 12, 2026 • 14:30",
            bankName = "BDO Unibank",
            accountNumber = "**** 8821"
        ),
        Payout(
            id = "po_2",
            reference = "PAY-20261008-002",
            amount = 620.50,
            status = "Completed",
            date = "Oct 08, 2026 • 16:15",
            bankName = "BDO Unibank",
            accountNumber = "**** 8821"
        ),
        Payout(
            id = "po_3",
            reference = "PAY-20261001-003",
            amount = 510.20,
            status = "Completed",
            date = "Oct 01, 2026 • 11:00",
            bankName = "BDO Unibank",
            accountNumber = "**** 8821"
        )
    )

    fun getInitialPromos(): List<PromoDeal> = listOf(
        PromoDeal(
            id = "promo_1",
            code = "DELIVIO20",
            title = "Weekend 20% Off All Burgers",
            discountPercentage = 20,
            minOrder = 15.00,
            startDate = "Oct 10, 2026",
            endDate = "Oct 20, 2026",
            isActive = true
        ),
        PromoDeal(
            id = "promo_2",
            code = "FEAST10",
            title = "Family Feast 10% Discount",
            discountPercentage = 10,
            minOrder = 25.00,
            startDate = "Oct 01, 2026",
            endDate = "Oct 31, 2026",
            isActive = true
        )
    )

    fun getInitialStaff(): List<StaffMember> = listOf(
        StaffMember("stf_1", "Juan Dela Cruz", "Owner", "testm@gmail.com"),
        StaffMember("stf_2", "Maria Santos", "Store Manager", "maria.s@deliviomart.ph"),
        StaffMember("stf_3", "Aris Navarro", "Head Chef / Kitchen", "aris.n@deliviomart.ph"),
        StaffMember("stf_4", "Leah Gomez", "Cashier & Counter", "leah.g@deliviomart.ph")
    )
}
