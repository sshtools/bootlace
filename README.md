# Bootlace

A library that makes use of Java Module Layers to provide a low level framework for creating
dynamically extendible layered applications

Inspired by Lawrry, Bootlace was written from the ground up to meet our particular needs.

 * Layers defined either programmatically or via INI format files.
 * Can locate artifacts in local paths (e.g. development environments), local repositories ($HOME/.m2), or any folder
 * Layers can be configured as dynamic pointing to a directory. Zip files dropped in the directory will be loaded as new layers. Also expanded layers in the directory may be deleted and they will be removed.  
 * Can download artifacts from either Maven Central or NPM.
 * Transforms NPM packages into Jars which are exposed as resources loadable as any other Java resource.
 
## Components

 * Bootlace API. Provides interfaces and basic services. Will be exposed to all child layers
 * Bootlace Platform. The implementation of the Bootlace API, used to bootstrap and maintain a
   a layered application. Will be hidden from any child layers.
 * Bootlace Repositories. Responsible for downloading artifacts from remote repositories. This module
   has some external dependencies, so in order to keep the number of modules exposed at the 
   root layer to a minimum, this can be isolated in a separate layer. Layers in sibling branches
   may then configure the repositories provided by this *Global Layer*
 * Bootlace Maven Plugin. Add to your projects to generate plugin archives.
 
## Usage

## Bootstrap

You need a small amount of Java code to Bootstrap your application. Further configuration can
then be achieved either by more code, or using a `layers.ini` file or resource.

### From Configuration File

TODO

### Programatically

TODO

