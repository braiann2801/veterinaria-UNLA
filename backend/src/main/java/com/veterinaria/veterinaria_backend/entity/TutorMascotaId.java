package com.veterinaria.veterinaria_backend.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Clave compuesta de la tabla de vinculo Tutor-Mascota.
 * El identificador de la tupla es el par (tutorId, mascotaId).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TutorMascotaId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "tutor_id", nullable = false)
	private Long tutorId;

	@Column(name = "mascota_id", nullable = false)
	private Long mascotaId;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TutorMascotaId)) {
			return false;
		}
		TutorMascotaId that = (TutorMascotaId) o;
		return Objects.equals(tutorId, that.tutorId)
				&& Objects.equals(mascotaId, that.mascotaId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tutorId, mascotaId);
	}

	@Override
	public String toString() {
		return "TutorMascotaId{tutorId=" + tutorId + ", mascotaId=" + mascotaId + "}";
	}
}