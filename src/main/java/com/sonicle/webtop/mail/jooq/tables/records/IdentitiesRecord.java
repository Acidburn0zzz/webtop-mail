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
public class IdentitiesRecord extends org.jooq.impl.UpdatableRecordImpl<com.sonicle.webtop.mail.jooq.tables.records.IdentitiesRecord> implements org.jooq.Record8<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String> {

	private static final long serialVersionUID = -1995895866;

	/**
	 * Setter for <code>mail.identities.identity_id</code>.
	 */
	public void setIdentityId(java.lang.Integer value) {
		setValue(0, value);
	}

	/**
	 * Getter for <code>mail.identities.identity_id</code>.
	 */
	public java.lang.Integer getIdentityId() {
		return (java.lang.Integer) getValue(0);
	}

	/**
	 * Setter for <code>mail.identities.domain_id</code>.
	 */
	public void setDomainId(java.lang.String value) {
		setValue(1, value);
	}

	/**
	 * Getter for <code>mail.identities.domain_id</code>.
	 */
	public java.lang.String getDomainId() {
		return (java.lang.String) getValue(1);
	}

	/**
	 * Setter for <code>mail.identities.user_id</code>.
	 */
	public void setUserId(java.lang.String value) {
		setValue(2, value);
	}

	/**
	 * Getter for <code>mail.identities.user_id</code>.
	 */
	public java.lang.String getUserId() {
		return (java.lang.String) getValue(2);
	}

	/**
	 * Setter for <code>mail.identities.email</code>.
	 */
	public void setEmail(java.lang.String value) {
		setValue(3, value);
	}

	/**
	 * Getter for <code>mail.identities.email</code>.
	 */
	public java.lang.String getEmail() {
		return (java.lang.String) getValue(3);
	}

	/**
	 * Setter for <code>mail.identities.display_name</code>.
	 */
	public void setDisplayName(java.lang.String value) {
		setValue(4, value);
	}

	/**
	 * Getter for <code>mail.identities.display_name</code>.
	 */
	public java.lang.String getDisplayName() {
		return (java.lang.String) getValue(4);
	}

	/**
	 * Setter for <code>mail.identities.main_folder</code>.
	 */
	public void setMainFolder(java.lang.String value) {
		setValue(5, value);
	}

	/**
	 * Getter for <code>mail.identities.main_folder</code>.
	 */
	public java.lang.String getMainFolder() {
		return (java.lang.String) getValue(5);
	}

	/**
	 * Setter for <code>mail.identities.fax</code>.
	 */
	public void setFax(java.lang.Boolean value) {
		setValue(6, value);
	}

	/**
	 * Getter for <code>mail.identities.fax</code>.
	 */
	public java.lang.Boolean getFax() {
		return (java.lang.Boolean) getValue(6);
	}

	/**
	 * Setter for <code>mail.identities.identity_uid</code>.
	 */
	public void setIdentityUid(java.lang.String value) {
		setValue(7, value);
	}

	/**
	 * Getter for <code>mail.identities.identity_uid</code>.
	 */
	public java.lang.String getIdentityUid() {
		return (java.lang.String) getValue(7);
	}

	// -------------------------------------------------------------------------
	// Primary key information
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Record1<java.lang.Integer> key() {
		return (org.jooq.Record1) super.key();
	}

	// -------------------------------------------------------------------------
	// Record8 type implementation
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row8<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String> fieldsRow() {
		return (org.jooq.Row8) super.fieldsRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row8<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.String> valuesRow() {
		return (org.jooq.Row8) super.valuesRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Integer> field1() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.IDENTITY_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field2() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.DOMAIN_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field3() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.USER_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field4() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.EMAIL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field5() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.DISPLAY_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field6() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.MAIN_FOLDER;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Boolean> field7() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.FAX;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field8() {
		return com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES.IDENTITY_UID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Integer value1() {
		return getIdentityId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value2() {
		return getDomainId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value3() {
		return getUserId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value4() {
		return getEmail();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value5() {
		return getDisplayName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value6() {
		return getMainFolder();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Boolean value7() {
		return getFax();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value8() {
		return getIdentityUid();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value1(java.lang.Integer value) {
		setIdentityId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value2(java.lang.String value) {
		setDomainId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value3(java.lang.String value) {
		setUserId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value4(java.lang.String value) {
		setEmail(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value5(java.lang.String value) {
		setDisplayName(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value6(java.lang.String value) {
		setMainFolder(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value7(java.lang.Boolean value) {
		setFax(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord value8(java.lang.String value) {
		setIdentityUid(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentitiesRecord values(java.lang.Integer value1, java.lang.String value2, java.lang.String value3, java.lang.String value4, java.lang.String value5, java.lang.String value6, java.lang.Boolean value7, java.lang.String value8) {
		return this;
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a detached IdentitiesRecord
	 */
	public IdentitiesRecord() {
		super(com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES);
	}

	/**
	 * Create a detached, initialised IdentitiesRecord
	 */
	public IdentitiesRecord(java.lang.Integer identityId, java.lang.String domainId, java.lang.String userId, java.lang.String email, java.lang.String displayName, java.lang.String mainFolder, java.lang.Boolean fax, java.lang.String identityUid) {
		super(com.sonicle.webtop.mail.jooq.tables.Identities.IDENTITIES);

		setValue(0, identityId);
		setValue(1, domainId);
		setValue(2, userId);
		setValue(3, email);
		setValue(4, displayName);
		setValue(5, mainFolder);
		setValue(6, fax);
		setValue(7, identityUid);
	}
}
