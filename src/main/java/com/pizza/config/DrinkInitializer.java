package com.pizza.config;

import com.pizza.entity.Drink;
import com.pizza.repository.DrinkRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds popular drink entries on startup ONLY when the drinks table is empty.
 * Drink images are seeded using public Unsplash/Pexels image URLs.
 * The admin can later update images via the admin panel.
 */
@Component
@RequiredArgsConstructor
public class DrinkInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DrinkInitializer.class);

    private final DrinkRepository drinkRepository;

    @Override
    public void run(String... args) {
        if (drinkRepository.count() > 0) {
            log.info("Drinks already seeded, skipping DrinkInitializer.");
            return;
        }

        List<Drink> drinks = List.of(
            Drink.builder().name("Coca-Cola").category("Soft Drinks").price(new BigDecimal("60.00"))
                .description("The original classic cola with a crisp, refreshing taste. The world's favourite carbonated drink.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1554866585-cd94860890b7?w=400&q=80").available(true).build(),
            Drink.builder().name("Coca-Cola Large").category("Soft Drinks").price(new BigDecimal("80.00"))
                .description("Extra-large classic Coca-Cola for those extra thirsty moments.").size("Large")
                .imageUrl("https://images.unsplash.com/photo-1554866585-cd94860890b7?w=400&q=80").available(true).build(),
            Drink.builder().name("Diet Coke").category("Diet Drinks").price(new BigDecimal("65.00"))
                .description("All the great taste of Coca-Cola, zero sugar and zero calories. The perfect guilt-free refreshment.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1625772452859-1c03d884dcd7?w=400&q=80").available(true).build(),
            Drink.builder().name("Coke Zero Sugar").category("Zero Sugar").price(new BigDecimal("65.00"))
                .description("Zero sugar, full Coca-Cola flavour. Reformulated for the ultimate zero-sugar experience.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1625772452859-1c03d884dcd7?w=400&q=80").available(true).build(),
            Drink.builder().name("Pepsi").category("Soft Drinks").price(new BigDecimal("55.00"))
                .description("Bold, crisp and refreshing cola taste that has stood the test of time. Pepsi generation.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?w=400&q=80").available(true).build(),
            Drink.builder().name("Pepsi Black").category("Zero Sugar").price(new BigDecimal("60.00"))
                .description("The maximum cola taste with zero sugar. Pepsi Black - bold flavour, no compromise.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?w=400&q=80").available(true).build(),
            Drink.builder().name("Sprite").category("Soft Drinks").price(new BigDecimal("55.00"))
                .description("Crispy, clear and refreshing lemon-lime flavour with a refreshing fizz. Obey your thirst.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=400&q=80").available(true).build(),
            Drink.builder().name("7Up").category("Soft Drinks").price(new BigDecimal("50.00"))
                .description("The fresh, clean, lemon-lime taste that keeps you refreshed. Seven Up - feel the bubbles.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=400&q=80").available(true).build(),
            Drink.builder().name("Fanta Orange").category("Soft Drinks").price(new BigDecimal("55.00"))
                .description("Bursting with real orange flavour and a fun fizz. Fanta brings the fun in every sip.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=400&q=80").available(true).build(),
            Drink.builder().name("Mountain Dew").category("Soft Drinks").price(new BigDecimal("60.00"))
                .description("An exhilarating, citrus-charged rush of intense refreshment. Do the Dew.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1580910051074-3eb694886505?w=400&q=80").available(true).build(),
            Drink.builder().name("Red Bull Energy Drink").category("Energy Drinks").price(new BigDecimal("130.00"))
                .description("Red Bull gives you wings. Contains caffeine, taurine, B-vitamins and sugar for energy boost.").size("Small")
                .imageUrl("https://images.unsplash.com/photo-1630759903741-8d4cadb8e1d4?w=400&q=80").available(true).build(),
            Drink.builder().name("Red Bull Sugar Free").category("Sugar-Free Drinks").price(new BigDecimal("130.00"))
                .description("The same great Red Bull energy boost with zero sugar. Vitalise body and mind without the sugar.").size("Small")
                .imageUrl("https://images.unsplash.com/photo-1630759903741-8d4cadb8e1d4?w=400&q=80").available(true).build(),
            Drink.builder().name("Appy Fizz").category("Soft Drinks").price(new BigDecimal("70.00"))
                .description("The sparkling apple drink that adds fizz to every moment. A unique blend of apple juice with bubbly effervescence.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1613478223719-2ab802602423?w=400&q=80").available(true).build(),
            Drink.builder().name("Minute Maid Orange Juice").category("Juices").price(new BigDecimal("80.00"))
                .description("Pure, fresh-squeezed orange taste packed with Vitamin C. 100% real orange juice with no added sugar.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1621506289937-a8e4df240d0b?w=400&q=80").available(true).build(),
            Drink.builder().name("Tropicana Apple Juice").category("Juices").price(new BigDecimal("85.00"))
                .description("Fresh, natural apple juice with no preservatives. Made from the finest handpicked apples.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1598030304671-5aa1d6f9e76f?w=400&q=80").available(true).build(),
            Drink.builder().name("Tropicana Mixed Fruit").category("Juices").price(new BigDecimal("90.00"))
                .description("A delicious blend of multiple fruits — apple, mango, grape and more — in every refreshing sip.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=400&q=80").available(true).build(),
            Drink.builder().name("Mineral Water 500ml").category("Water").price(new BigDecimal("30.00"))
                .description("Pure, fresh mineral water sourced from natural springs. Crisp and refreshing with essential minerals.").size("Small")
                .imageUrl("https://images.unsplash.com/photo-1548839140-29a749e1cf4d?w=400&q=80").available(true).build(),
            Drink.builder().name("Mineral Water 1L").category("Water").price(new BigDecimal("50.00"))
                .description("Large bottle of pure mineral water — perfect for sharing or staying well-hydrated all day long.").size("Large")
                .imageUrl("https://images.unsplash.com/photo-1548839140-29a749e1cf4d?w=400&q=80").available(true).build(),
            Drink.builder().name("Cappuccino").category("Coffee").price(new BigDecimal("120.00"))
                .description("Rich espresso combined with perfectly steamed milk foam. The classic Italian coffeehouse experience.").size("Medium")
                .imageUrl("https://images.unsplash.com/photo-1534778101976-62847782c213?w=400&q=80").available(true).build(),
            Drink.builder().name("Masala Chai").category("Tea").price(new BigDecimal("60.00"))
                .description("Traditional Indian spiced tea brewed with ginger, cardamom, cinnamon and cloves in full-bodied milk.").size("Small")
                .imageUrl("https://images.unsplash.com/photo-1561336313-0bd5e0b27ec8?w=400&q=80").available(true).build(),
            Drink.builder().name("Chocolate Milkshake").category("Milkshakes").price(new BigDecimal("150.00"))
                .description("Thick, creamy chocolate milkshake blended with premium chocolate ice cream and fresh whole milk.").size("Large")
                .imageUrl("https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=400&q=80").available(true).build(),
            Drink.builder().name("Vanilla Milkshake").category("Milkshakes").price(new BigDecimal("140.00"))
                .description("Smooth, velvety vanilla milkshake made from real vanilla beans and creamy farm-fresh milk.").size("Large")
                .imageUrl("https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=400&q=80").available(true).build()
        );

        drinkRepository.saveAll(drinks);
        log.info("Seeded {} drinks into the drinks catalogue.", drinks.size());
    }
}
