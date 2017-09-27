import { NativeModules } from 'react-native';

const GeetestModule = NativeModules.GeetestModule;

function setDebugMode(debugMode) {
  return GeetestModule.setDebugMode(debugMode);
}

function configure(captchaId, challenge, successCode) {
  return GeetestModule.configure(captchaId, challenge, successCode);
}

function openGTView(animated) {
  return GeetestModule.openGTView(animated);
}

export default {
  setDebugMode,
  configure,
  openGTView
};
