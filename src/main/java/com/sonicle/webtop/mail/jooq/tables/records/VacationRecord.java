/**
 * This class is generated by jOOQ
 */
package com.sonicle.webtop.mail.jooq.tables.records;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.5.3"
	},
	comments = "This class is generated by jOOQ"
)
@java.lang.SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class VacationRecord extends org.jooq.impl.UpdatableRecordImpl<com.sonicle.webtop.mail.jooq.tables.records.VacationRecord> implements org.jooq.Record5<java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String, java.lang.String> {

	private static final long serialVersionUID = -198379527;

	/**
	 * Setter for <code>mail.vacation.domain_id</code>.
	 */
	public void setDomainId(java.lang.String value) {
		setValue(0, value);
	}

	/**
	 * Getter for <code>mail.vacation.domain_id</code>.
	 */
	public java.lang.String getDomainId() {
		return (java.lang.String) getValue(0);
	}

	/**
	 * Setter for <code>mail.vacation.user_id</code>.
	 */
	public void setUserId(java.lang.String value) {
		setValue(1, value);
	}

	/**
	 * Getter for <code>mail.vacation.user_id</code>.
	 */
	public java.lang.String getUserId() {
		return (java.lang.String) getValue(1);
	}

	/**
	 * Setter for <code>mail.vacation.active</code>.
	 */
	public void setActive(java.lang.Boolean value) {
		setValue(2, value);
	}

	/**
	 * Getter for <code>mail.vacation.active</code>.
	 */
	public java.lang.Boolean getActive() {
		return (java.lang.Boolean) getValue(2);
	}

	/**
	 * Setter for <code>mail.vacation.message</code>.
	 */
	public void setMessage(java.lang.String value) {
		setValue(3, value);
	}

	/**
	 * Getter for <code>mail.vacation.message</code>.
	 */
	public java.lang.String getMessage() {
		return (java.lang.String) getValue(3);
	}

	/**
	 * Setter for <code>mail.vacation.addresses</code>.
	 */
	public void setAddresses(java.lang.String value) {
		setValue(4, value);
	}

	/**
	 * Getter for <code>mail.vacation.addresses</code>.
	 */
	public java.lang.String getAddresses() {
		return (java.lang.String) getValue(4);
	}

	// -------------------------------------------------------------------------
	// Primary key information
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Record3<java.lang.String, java.lang.String, java.lang.Boolean> key() {
		return (org.jooq.Record3) super.key();
	}

	// -------------------------------------------------------------------------
	// Record5 type implementation
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row5<java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String, java.lang.String> fieldsRow() {
		return (org.jooq.Row5) super.fieldsRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row5<java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String, java.lang.String> valuesRow() {
		return (org.jooq.Row5) super.valuesRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field1() {
		return com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION.DOMAIN_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field2() {
		return com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION.USER_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Boolean> field3() {
		return com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION.ACTIVE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field4() {
		return com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION.MESSAGE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field5() {
		return com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION.ADDRESSES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value1() {
		return getDomainId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value2() {
		return getUserId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Boolean value3() {
		return getActive();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value4() {
		return getMessage();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value5() {
		return getAddresses();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord value1(java.lang.String value) {
		setDomainId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord value2(java.lang.String value) {
		setUserId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord value3(java.lang.Boolean value) {
		setActive(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord value4(java.lang.String value) {
		setMessage(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord value5(java.lang.String value) {
		setAddresses(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VacationRecord values(java.lang.String value1, java.lang.String value2, java.lang.Boolean value3, java.lang.String value4, java.lang.String value5) {
		return this;
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a detached VacationRecord
	 */
	public VacationRecord() {
		super(com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION);
	}

	/**
	 * Create a detached, initialised VacationRecord
	 */
	public VacationRecord(java.lang.String domainId, java.lang.String userId, java.lang.Boolean active, java.lang.String message, java.lang.String addresses) {
		super(com.sonicle.webtop.mail.jooq.tables.Vacation.VACATION);

		setValue(0, domainId);
		setValue(1, userId);
		setValue(2, active);
		setValue(3, message);
		setValue(4, addresses);
	}
}