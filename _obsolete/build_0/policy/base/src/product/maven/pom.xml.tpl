<project xmlns="http://maven.apache.org/POM/4.0.0"
		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<!-- Published parent POM generated from this template -->
	<groupId>@algites.policy.artifact.groupId@</groupId>
	<artifactId>@algites.policy.artifact.artifactId@-maven</artifactId>
	<version>@algites.repository.lane@.@algites.repository.lane.revision@@algites.repository.lane.revision.suffix@</version>
	<packaging>pom</packaging>

	<name>Algites Build - Base (Maven parent)</name>
	<description>Base build conventions for Algites projects (engine: Maven).</description>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

		<!-- Governance -->
		<policy.version_@algites.policy.id@>@algites.policy.version@</policy.version_@algites.policy.id@>

		<!-- Tooling -->
		<algites.maven.enforcer.version>3.5.0</algites.maven.enforcer.version>
	</properties>

	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-enforcer-plugin</artifactId>
				<version>${algites.maven.enforcer.version}</version>
				<executions>
					<execution>
						<id>enforce-algites-build-base-pub-policy-version</id>
						<phase>validate</phase>
						<goals>
							<goal>enforce</goal>
						</goals>
						<configuration>
							<rules>
								<requireProperty>
									<property>algites.build.base.pub.policy.version</property>
									<regex>^${project.parent.properties.algites.build.base.pub.policy.version}$</regex>
									<message>
										Algites build base public policy version must not be overridden.
									</message>
								</requireProperty>
							</rules>
							<fail>true</fail>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
	<!-- Repositories for dependency resolution (PUBLIC baseline) -->
	<repositories>
		<!-- Maven Central (releases) -->
		<repository>
			<id>central</id>
			<url>https://repo1.maven.org/maven2</url>
			<releases>
				<enabled>true</enabled>
			</releases>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</repository>

		<!-- Cloudsmith public snapshots -->
		<repository>
			<id>algites-public-snapshots</id>
			<url>https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/</url>
			<releases>
				<enabled>false</enabled>
			</releases>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</repository>
	</repositories>

	<!-- Plugin repositories (PUBLIC baseline) -->
	<pluginRepositories>
		<pluginRepository>
			<id>central</id>
			<url>https://repo1.maven.org/maven2</url>
			<releases>
				<enabled>true</enabled>
			</releases>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</pluginRepository>

		<pluginRepository>
			<id>algites-public-snapshots</id>
			<url>https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/</url>
			<releases>
				<enabled>false</enabled>
			</releases>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</pluginRepository>
	</pluginRepositories>

	<distributionManagement>
		<repository>
			<id>algites-public-releases-upload</id>
			<url>https://central.sonatype.com/service/local/staging/deploy/maven2/</url>
		</repository>
		<snapshotRepository>
			<id>algites-public-snapshots-upload</id>
			<url>https://dl.cloudsmith.io/maven/algites/maven-snapshots-pub/maven/</url>
<!--			<url>https://maven.cloudsmith.io/algites/maven-snapshots-pub/</url>-->
		</snapshotRepository>
	</distributionManagement>

</project>
