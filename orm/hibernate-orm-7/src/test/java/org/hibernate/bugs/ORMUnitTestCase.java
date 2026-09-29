package org.hibernate.bugs;

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.annotations.ConcreteProxy;
import org.hibernate.cfg.AvailableSettings;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using its built-in unit test framework.
 * Although ORMStandaloneTestCase is perfectly acceptable as a reproducer, usage of this class is much preferred.
 * Since we nearly always include a regression test with bug fixes, providing your reproducer using this method
 * simplifies the process.
 * <p>
 * What's even better?  Fork hibernate-orm itself, add your test case directly to a module's unit tests, then
 * submit it as a PR!
 */
@DomainModel(
		annotatedClasses = {
				ORMUnitTestCase.User.class,
				ORMUnitTestCase.CompanyUser.class,
				ORMUnitTestCase.SubscriberUser.class,
				ORMUnitTestCase.Case.class,
		},
		// If you use *.hbm.xml mappings, instead of annotations, add the mappings here.
		xmlMappings = {
				// "org/hibernate/test/Foo.hbm.xml",
				// "org/hibernate/test/Bar.hbm.xml"
		}
)
@ServiceRegistry(
		// Add in any settings that are specific to your test.  See resources/hibernate.properties for the defaults.
		settings = {
				// For your own convenience to see generated queries:
				@Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
				@Setting(name = AvailableSettings.FORMAT_SQL, value = "true"),
				// @Setting( name = AvailableSettings.GENERATE_STATISTICS, value = "true" ),

				// Add your own settings that are a part of your quarkus configuration:
				// @Setting( name = AvailableSettings.SOME_CONFIGURATION_PROPERTY, value = "SOME_VALUE" ),
		}
)
@SessionFactory
class ORMUnitTestCase {

	@BeforeEach
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			CompanyUser user = new CompanyUser( 1L );
			session.persist( user );
			session.persist( new Case( 1L, user ) );
		} );
	}

	@AfterEach
	void tearDown(SessionFactoryScope scope) {
		scope.getSessionFactory().getSchemaManager().truncateMappedObjects();
	}

	@Test
	void withoutImplicitJoinWorks(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			List<Case> cases = session.createSelectionQuery( "from Case c", Case.class ).getResultList();
			assertThat( cases ).hasSize( 1 );
			assertThat( cases.get( 0 ).submittedBy ).isInstanceOf( CompanyUser.class );
		} );
	}

	@Test
	void hql(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			List<Case> cases = session.createSelectionQuery(
							"from Case c where c.submittedBy.id in (:ids)", Case.class )
					.setParameter( "ids", List.of( 1L ) )
					.getResultList();
			assertThat( cases ).hasSize( 1 );
			assertThat( cases.get( 0 ).submittedBy ).isInstanceOf( CompanyUser.class );
		} );
	}

	@Test
	void criteria(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<Case> query = cb.createQuery( Case.class );
			Root<Case> root = query.from( Case.class );
			query.where( root.get( "submittedBy" ).get( "id" ).in( List.of( 1L ) ) );
			List<Case> cases = session.createQuery( query ).getResultList();
			assertThat( cases ).hasSize( 1 );
			assertThat( cases.get( 0 ).submittedBy ).isInstanceOf( CompanyUser.class );
		} );
	}

	@Test
	void hqlExplicitJoin(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			List<Case> cases = session.createSelectionQuery(
							"select c from Case c join c.submittedBy u where u.id in (:ids)", Case.class )
					.setParameter( "ids", List.of( 1L ) )
					.getResultList();
			assertThat( cases ).hasSize( 1 );
			assertThat( cases.get( 0 ).submittedBy ).isInstanceOf( CompanyUser.class );
		} );
	}

	@Test
	void criteriaExplicitJoin(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<Case> query = cb.createQuery( Case.class );
			Root<Case> root = query.from( Case.class );
			query.where( root.join( "submittedBy" ).get( "id" ).in( List.of( 1L ) ) );
			List<Case> cases = session.createQuery( query ).getResultList();
			assertThat( cases ).hasSize( 1 );
			assertThat( cases.get( 0 ).submittedBy ).isInstanceOf( CompanyUser.class );
		} );
	}

	@Entity(name = "User")
	@Table(name = "app_user")
	@ConcreteProxy
	@Inheritance(strategy = InheritanceType.JOINED)
	public static abstract class User {
		@Id
		Long id;

		User() {
		}

		User(Long id) {
			this.id = id;
		}
	}

	@Entity(name = "CompanyUser")
	public static class CompanyUser extends User {
		CompanyUser() {
		}

		CompanyUser(Long id) {
			super( id );
		}
	}

	@Entity(name = "SubscriberUser")
	public static class SubscriberUser extends User {
	}

	@Entity(name = "Case")
	@Table(name = "app_case")
	public static class Case {
		@Id
		Long id;

		@ManyToOne(fetch = FetchType.LAZY, optional = false)
		User submittedBy;

		Case() {
		}

		Case(Long id, User submittedBy) {
			this.id = id;
			this.submittedBy = submittedBy;
		}
	}
}
