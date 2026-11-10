package kz.gymbooking.dto;

import java.net.URI;
import java.util.List;

public record TrainerUpdate(List<String> specializations, String bio, URI photoUrl) {
}
