package com.example.exam.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LoginRequest(
	@JsonAlias({"username", "email"}) String email,
	String password
) {
}
