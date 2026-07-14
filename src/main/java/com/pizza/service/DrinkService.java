package com.pizza.service;

import com.pizza.dto.DrinkDTO;
import com.pizza.entity.Drink;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.DrinkRepository;
import com.pizza.service.CloudinaryService.UploadResult;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic for the drink catalogue: browsing, searching, sorting and admin CRUD with Cloudinary.
 */
@Service
@RequiredArgsConstructor
public class DrinkService {

    private final DrinkRepository drinkRepository;
    private final CloudinaryService cloudinaryService;

    public static final List<String> PREDEFINED_CATEGORIES = List.of(
            "Soft Drinks", "Diet Drinks", "Sugar-Free Drinks", "Zero Sugar", 
            "Juices", "Coffee", "Tea", "Energy Drinks", "Milkshakes", "Water"
    );

    @Transactional(readOnly = true)
    public List<Drink> findAll() {
        return drinkRepository.findAll();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return drinkRepository.count();
    }

    @Transactional(readOnly = true)
    public long countAvailable() {
        return drinkRepository.findByAvailableTrue().size();
    }

    @Transactional(readOnly = true)
    public long countOutOfStock() {
        return countAll() - countAvailable();
    }

    @Transactional(readOnly = true)
    public List<Drink> search(String search, String category, String sort) {
        boolean hasSearch = StringUtils.hasText(search);
        boolean hasCategory = StringUtils.hasText(category);

        List<Drink> results;
        if (hasSearch && hasCategory) {
            results = drinkRepository.findByCategoryAndNameContainingIgnoreCase(category, search);
        } else if (hasCategory) {
            results = drinkRepository.findByCategory(category);
        } else if (hasSearch) {
            results = drinkRepository.findByNameContainingIgnoreCase(search);
        } else {
            results = drinkRepository.findAll();
        }

        if ("priceAsc".equals(sort)) {
            results.sort(Comparator.comparing(Drink::getPrice));
        } else if ("priceDesc".equals(sort)) {
            results.sort(Comparator.comparing(Drink::getPrice).reversed());
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<String> findCategories() {
        return drinkRepository.findAll().stream()
                .map(Drink::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public Drink findById(Long id) {
        return drinkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drink not found: id " + id));
    }

    @Transactional
    public Drink add(DrinkDTO dto, MultipartFile image) {
        UploadResult upload = cloudinaryService.upload(image);
        try {
            Drink drink = Drink.builder()
                    .name(dto.getName().trim())
                    .description(dto.getDescription().trim())
                    .category(dto.getCategory().trim())
                    .price(dto.getPrice())
                    .size(dto.getSize().trim())
                    .imageUrl(upload.secureUrl())
                    .imagePublicId(upload.publicId())
                    .available(dto.isAvailable())
                    .build();
            return drinkRepository.saveAndFlush(drink);
        } catch (RuntimeException ex) {
            cloudinaryService.delete(upload.publicId());
            throw ex;
        }
    }

    @Transactional
    public Drink update(Long id, DrinkDTO dto, MultipartFile image) {
        Drink drink = findById(id);
        String oldPublicId = drink.getImagePublicId();

        drink.setName(dto.getName().trim());
        drink.setDescription(dto.getDescription().trim());
        drink.setCategory(dto.getCategory().trim());
        drink.setPrice(dto.getPrice());
        drink.setSize(dto.getSize().trim());
        drink.setAvailable(dto.isAvailable());

        UploadResult newUpload = null;
        if (image != null && !image.isEmpty()) {
            newUpload = cloudinaryService.upload(image);
            drink.setImageUrl(newUpload.secureUrl());
            drink.setImagePublicId(newUpload.publicId());
        }

        try {
            Drink saved = drinkRepository.saveAndFlush(drink);
            if (newUpload != null && oldPublicId != null && !oldPublicId.isBlank()) {
                cloudinaryService.delete(oldPublicId);
            }
            return saved;
        } catch (RuntimeException ex) {
            if (newUpload != null) {
                cloudinaryService.delete(newUpload.publicId());
            }
            throw ex;
        }
    }

    @Transactional
    public void delete(Long id) {
        Drink drink = findById(id);
        String publicId = drink.getImagePublicId();

        try {
            drinkRepository.delete(drink);
            drinkRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "This drink cannot be deleted because it has already been ordered.", ex);
        }

        cloudinaryService.delete(publicId);
    }

    public DrinkDTO toDto(Drink drink) {
        DrinkDTO dto = new DrinkDTO();
        dto.setId(drink.getId());
        dto.setName(drink.getName());
        dto.setDescription(drink.getDescription());
        dto.setCategory(drink.getCategory());
        dto.setPrice(drink.getPrice());
        dto.setSize(drink.getSize());
        dto.setImageUrl(drink.getImageUrl());
        dto.setAvailable(drink.isAvailable());
        return dto;
    }
}
