package com.victorpena.contacttracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scrape_runs")
public class ScrapeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScrapeRunStatus status = ScrapeRunStatus.STARTED;

    @Column(name = "books_found", nullable = false)
    private int booksFound;

    @Column(name = "books_added", nullable = false)
    private int booksAdded;

    @Column(name = "books_updated", nullable = false)
    private int booksUpdated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ScrapeRun() {
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}

	public ScrapeRunStatus getStatus() {
		return status;
	}

	public void setStatus(ScrapeRunStatus status) {
		this.status = status;
	}

	public int getBooksFound() {
		return booksFound;
	}

	public void setBooksFound(int booksFound) {
		this.booksFound = booksFound;
	}

	public int getBooksAdded() {
		return booksAdded;
	}

	public void setBooksAdded(int booksAdded) {
		this.booksAdded = booksAdded;
	}

	public int getBooksUpdated() {
		return booksUpdated;
	}

	public void setBooksUpdated(int booksUpdated) {
		this.booksUpdated = booksUpdated;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

    
}