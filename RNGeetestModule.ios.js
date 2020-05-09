function setDebugMode(debugMode) {}

function configure(captchaId, challenge, successCode) {
  return Promise.resolve({});
}

function openGTView(animated) {
  return Promise.reject(new Error('Not Support!'));
}

export default {
  setDebugMode,
  configure,
  openGTView
};
