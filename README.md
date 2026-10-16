# Delivio Partner

High-converting, production-quality Android merchant app for the **Delivio** 4-product delivery platform (Customer, Merchant, Rider, Admin). Built using **Kotlin**, **Jetpack Compose**, and **Material Design 3** following MVVM architecture with StateFlow.

---

## 🔑 Demo Login Credentials
- **Email:** `testm@gmail.com`
- **Password:** `Lidesma@1234`
- **Role:** Merchant
- *(A one-tap "Use Demo Credentials" shortcut is also provided on the login screen.)*

---

## 🚀 Key Features

1. **Brand & Dynamic Theme Engine:**
   - Default warm accent color: Yellow (`#FFC107`), with automatic contrast text calculation.
   - **Choose Merchant Theme Color:** Preset swatches (Yellow, Orange, Red, Green, Blue, Purple, Pink, Teal) + custom HEX input with instant app-wide persistence.
   - **Light / Dark Mode Switch:** Seamlessly toggles between light surface styling and rich `#121212` / `#1E1E1E` dark mode.

2. **Global Open / Close & Kitchen Pause:**
   - Prominent Open/Close switch on Dashboard with a top-bar status chip mirrored across all tabs.
   - Store close confirmation protection when active orders are still preparing.
   - Kitchen pause options (15, 30, 60 minutes) with remaining time display.
   - Mon–Sun weekly operating schedule.

3. **Standard Order Status Lifecycle:**
   - Complete progression: `Placed Order` → `Preparing` → `Ready` → `Rider is on the way` → `Delivered` (+ `Rejected` & `Cancelled`).
   - Merchant controls: Accept with prep-time selection (10, 15, 20, 30, 45 min) and active countdown timer, reject with reasons dialog, and mark ready.
   - Real-time new order heads-up alert with **60-second auto-expire countdown**, sound, and haptic vibration.

4. **Ready Order Actions & Thermal Printing:**
   - **Print Receipt:** Formatted thermal receipt preview (58mm/80mm) integrated with Android `PrintManager` and WebView printing.
   - **Share Receipt:** Instant PDF/text share intent.
   - Customer phone dialer (`Intent.ACTION_DIAL`) enabled on placement.

5. **Request a Rider (Manual Dispatch & Live Tracking):**
   - Book nearby rider with pickup point (store map location) and customer drop-off.
   - Simulated 5–8 second searching state, rider assignment (name, vehicle plate, rating, ETA, call dialer).
   - Live interactive map route tracking rider progression from merchant to customer.
   - Self-delivery alternative option.

6. **Full Menu Management:**
   - Dynamic fields automatically adapted for **Food & Dining** (prep time, modifiers, sizes, cuisine tags) vs. **Convenience Store** (SKU, barcode, stock count, units).
   - Per-item stock availability switch (Sold Out durations: until turned back on, today only, 1/2/4 hours).
   - Bulk actions: mark in-stock/sold-out, category reassignment, price adjustments (+/- %).
   - Category management (add, edit, reorder up/down, delete).

7. **Dashboard & Analytics:**
   - KPI metrics: Today's Orders, Revenue, Average Prep Time, Acceptance Rate (98.4%), Rating (4.9★).
   - Interactive Compose Canvas charts: Orders by hour bar chart and 7-day revenue trend.

8. **Earnings & Payouts:**
   - Today / This Week / This Month filters, 15% platform fee deduction breakdown, and pending balance.
   - Bank details management and instant "Request Payout" simulation.
   - Export and share financial statement report.

9. **Interactive Map Location Setter:**
   - Draggable pin canvas with runtime device location permission to set store pickup coordinates.
