# Moqui Hazelcast Tool Component

[![license](http://img.shields.io/badge/license-CC0%201.0%20Universal-blue.svg)](https://github.com/moqui/moqui-hazelcast/blob/master/LICENSE.md)
[![release](http://img.shields.io/github/release/moqui/moqui-hazelcast.svg)](https://github.com/moqui/moqui-hazelcast/releases)

Moqui Framework tool component for Hazelcast, used for:

- distributed async service execution
- entity distributed cache invalidation
- web session replication
- distributed cache (javax.cache implementation)

This is the recommended component to add and configure for deployments with more than one application server (more than
one server in a cluster running Moqui).

To install run (with moqui-framework):

    $ ./gradlew getComponent -Pcomponent=moqui-hazelcast

This will add the component to the Moqui runtime/component directory. 

The Hazelcast and dependent JAR files are added to the lib directory when the build is run for this component, which is
designed to be done from the Moqui build (ie from the moqui root directory) along with all other component builds. 

To use just install this component. All configuration is already in place in the MoquiConf.xml included in this 
component and will be merged with the main configuration at runtime. To modify any of the settings from the defaults 
here simply add them to your runtime Moqui Conf XML file.

For normal operation there is very little overhead adding this component, except that the entity distributed cache 
invalidate is enabled by default when this component is in place (enabled in the MoquiConf.xml file in this component). 
If you don't want this just set entity-facade.@distributed-cache-invalidate to false in your runtime configuration.
