/**
 * @providesModule RNGeetestModule
 * @flow
 */
'use strict';

var NativeRNGeetestModule = require('NativeModules').RNGeetestModule;

/**
 * High-level docs for the RNGeetestModule iOS API can be written here.
 */

var RNGeetestModule = {
  test: function() {
    NativeRNGeetestModule.test();
  }
};

module.exports = RNGeetestModule;
