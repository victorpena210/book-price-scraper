package com.victorpena.contacttracker.scraper;

import java.math.BigDecimal;

public record ScrapedBook(
		String title,
		String productUrl,
		String imageUrl,
		String category,
		BigDecimal price,
		String availability,
		Byte rating
		) {
}
