package it.fvaleri.example;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "audit_log")
@NamedQueries({
	@NamedQuery(name = "getAuditLog", query = "select al from AuditLog al")
})
public class AuditLog {
	@Id
	@GeneratedValue (strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private long id;
	private String message;

	public String getMessage() {
		return message;
	}

	public AuditLog createAuditLog(String message) {
		AuditLog auditLog = new AuditLog();
		auditLog.message = message;
		return auditLog;
	}

	@Override
	public String toString() {
		return String.format("{message=%s}", message);
	}
}
