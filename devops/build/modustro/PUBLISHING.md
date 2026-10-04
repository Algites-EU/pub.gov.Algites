# Output selectors and publication records

## Output categories and groups

The eight publishing categories are `native_product_sources`, `native_product_binaries`,
`native_product_documentation`, `native_develop_sources`, `native_develop_binaries`,
`native_develop_documentation`, `modustro_docs_site`, and `schema_site`.
Native producer types such as `java_classes_jar`, `java_sources_jar`, `java_javadoc_jar`,
`python_wheel`, and `python_sdist` remain separate from publishing categories.

Virtual selectors exist only in descriptor configuration: `native_outputs` selects all six
native categories; `native_product_outputs` and `native_develop_outputs` select three each;
`native_sources`, `native_binaries`, and `native_documentation` select both scopes.

`ResourceEndpoints.<technology>` and `OutputPublishing` are ordered lists. Within a descriptor,
`native_outputs` applies first, other groups apply in their declaration order, and concrete
categories apply last. The next hierarchy level then overrides this result: repository,
artifact-set, artifact. An artifact-level group can therefore override a repository-level
concrete declaration. Lists of publications merge by sibling `Id`; explicit `[]` clears them.
`TechnologyKind` optionally scopes a publishing declaration, for example to `java` or `python`.

```yaml
ResourceEndpoints:
  java:
    - OutputSelector: native_outputs
      Enabled: true
      public:
        upload:
          - Id: example-java-native-outputs-public-snapshot-upload
            Url: https://repo.example.org/maven/
            Stability: snapshot
    - OutputSelector: native_develop_outputs
      Enabled: false
    - OutputSelector: native_develop_binaries
      Enabled: true
```

`Enabled` controls endpoint availability. It does not enable production or publishing.
`Snapshot.PublishingEnabled` / `Release.PublishingEnabled` control publication of an output.
Native production remains controlled by `TechnologyKinds.Items[].BuildOutputTypes` and
capability configuration. Develop publications default to disabled. The built-in Java
adapter can package develop classes, sources and Javadoc; the Python adapter can package
develop wheels and sdists from `src/develop/python` and generated develop sources. It fails
early when develop packaging is enabled without develop Python roots. Native Python
reference documentation has no built-in archive producer; a technology adapter must supply
that payload. A selector alone never fabricates an output.

## Independent main forms and recursive extensions

```yaml
OutputPublishing:
  - OutputSelector: native_product_outputs
    TechnologyKind: java
    Snapshot:
      PublishingEnabled: true
      EndpointPublications:
        - Id: example-maven
          PublishingUrl: https://repo.example.org/maven/
          PublishingAdapter: maven-repository
          PublishingOrder: 0
          PublishingRetryCount: 2
          PublishingRetryDelayMillis: 1000
          PublishingAttemptTimeoutMillis: 30000
          PublishingFailurePolicy: FAIL_BUILD_ON_PUBLISHING_FAILURE
          Publications:
            - Id: standard
              Classifier: ""
              Extension: jar
              ExtendedPublications:
                - Id: build-record
                  PublicationProducer: modustro-build-record
                  PublishingEnabled: true
                  PublishingRetryCount: 3
            - Id: custom
              Classifier: tests.extra
              Extension: jar
              ExtendedPublications:
                - Id: build-record
                  PublicationProducer: modustro-build-record
                  PublishingEnabled: false
```

Omitting `Publications` selects one `standard` form with the producer's classifier and
extension. Renaming a form does not transform its contents. Extensions can transform or
analyze content through `AIiPublicationProducer`, registered directly or with ServiceLoader.
Each main form and each enabled extension is a separate adapter invocation and retry job.
Main forms share the outer policy; put forms in different `EndpointPublications` entries
when they need different failure or timeout policies.

An extension inherits its immediate parent's transport, URL, credentials, order and retry
policy, with optional explicit overrides. It runs only after successful parent upload and
receives that parent's immutable payload and result, plus invocation provenance. Explicit
recursive `ExtendedPublications` are supported, with unique sibling IDs and maximum depth
64. Extension order cannot precede parent order. A failed ignored parent skips its descendants.
A required extension failure fails the build even if the parent's policy allowed failures.
Retries of a sidecar never repeat the successful main upload; there is no rollback.

Every main form gets an automatic `build-record` extension. An explicit sibling with that
Id overrides or disables it. Extensions do not automatically receive further records;
record-of-record is possible only by explicit finite configuration.

## Transport and exact filenames

A record's name is exactly `<published filename>.modustro-build-record.yml`, including the
original archive extension. For Maven this uses the original classifier and compound
extension, e.g. `javadoc` + `jar.modustro-build-record.yml`. Dotted custom classifiers are
supported. Remote Maven snapshots reserve one timestamp/build number for all forms and
records in the invocation. Version-level `maven-metadata.xml` contains compound extension
entries. Retry compares existing remote bytes and refuses to overwrite a different file.
Parallel metadata updates are serialized within the build process; independent concurrent
publishers still require repository-side coordination. Release coordinates are immutable.

Python uses the same filename convention for wheels and sdists. Standard Python indexes
accept distributions, not arbitrary YAML. Configure a separate record destination using
`local-copy`, `http-directory`, `git-branch`, or another provider adapter, for example:

```yaml
Publications:
  - Id: standard
    ExtendedPublications:
      - Id: build-record
        PublicationProducer: modustro-build-record
        PublishingUrl: https://records.example.org/packages/
        PublishingAdapter: http-directory
        PublishingCredentialProfile: records-upload
```

Governed Python defaults explicitly disable records until such a destination is configured.
No Cloudsmith-specific sidecar storage is assumed. Python snapshot distributions use the
existing timestamp-based snapshot instance convention, never the placeholder `1.0.dev0`.

## Record and cache boundaries

`modustro-build-record_1.yamldef.schema.json` specifies a common versioned document for every
technology. It contains `Artifact` (effective required GroupId and logical version), `Output`
(final published version, classifier, extension and file SHA-256/size), `Invocation`,
`Sources`, `Tools`, `OutputOrigin`, and `ParentPublication`. JSON serialization is used as
a valid YAML 1.2 subset. Unknown revisions and unobservable cache origins are explicitly
`unknown`; they are never guessed from a version string.

Invocation data is generated at execution time outside the cacheable archive and embedded
`modustro-artifact-manifest.yml`. Thus changing a repository revision or workflow run does
not alone invalidate an archive's build cache. Effective GroupId is mandatory in the embedded
manifest and is checked while resolving artifact metadata before build tasks execute.

Workflow logs identify source, public governance and, for private workers, private governance
revisions. Shared source/default/license downloads are pinned to the logged governance SHA
when supplied by the workflow. Public docs defaults and deployment endpoints live in
`repository/defaults/algites-repository-defaults-public.yml`; public documentation no longer
fetches private governance or its secrets. Upload credentials remain in private-governance
configuration, with secret values supplied outside descriptors.

## Adoption

Publish the supplied generator hotfix and new Builder bootstrap binaries before applying
new descriptors to downstream CI. Old bootstrap plugins cannot read the new selector lists.
Run the supplied git-move script in the public repository before overlaying the source ZIP;
it also supports running after overlay. Do not delete artifact `build.gradle.kts` files or
existing Modustro dependency declarations during this migration.

The complete fixture is `builder/gradleinit/src/develop/resources/publication-tree.yml`.
Tests cover precedence, sparse recursion, retry, required child failure, Python transport
validation, exact naming, Maven HTTP metadata, GroupId preflight and configuration cache.
