package com.veterinaria.veterinaria_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tupla de la relacion N:M entre {@link Tutor} y {@link Mascota}.
 *
 * <p>Cada tupla registra de forma explicita el flag {@code autorizadoRetiro}
 * (regla de negocio 2.4) y la fecha en que se genero el vinculo para auditoria.</p>
 */
@Entity
@Table(name = "tutor_mascota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TutorMascota {

	@EmbeddedId
	private TutorMascotaId id;

	@MapsId("tutorId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tutor_id", nullable = false)
	private Tutor tutor;

	@MapsId("mascotaId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mascota_id", nullable = false)
	private Mascota mascota;

	/** Indica si este tutor/cotutor esta habilitado para retirar a la mascota. */
	@Column(name = "autorizado_retiro", nullable = false)
	private boolean autorizadoRetiro = false;

	@Column(name = "vinculado_desde")
	private LocalDateTime vinculadoDesde;

	/**
	 * No se asigna el id a mano: con {@code @MapsId} es Hibernate quien lo
	 * deriva de {@code tutor.id} y {@code mascota.id} al persistir. Construirlo
	 * aqui genera una tupla "separada" que el contexto de persistencia rechaza
	 * con {@code NonUniqueObjectException} cuando la relacion ya existe.
	 */
	public TutorMascota(Tutor tutor, Mascota mascota, boolean autorizadoRetiro) {
		this.tutor = tutor;
		this.mascota = mascota;
		this.autorizadoRetiro = autorizadoRetiro;
	}
}