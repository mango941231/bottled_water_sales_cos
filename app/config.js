// Local development server configuration.
const SERVER_ORIGIN = 'http://81.68.239.216';

module.exports = {
  // Set to false before release to use the normal WeChat authorization flow.
  MOCK_LOGIN: true,
  MOCK_USER: {
    id: 3,
    userName: '测试用户',
    avatar: '/img/logo.png',
    type: 1,
    loginMode: 'mock'
  },
  SERVER_ORIGIN,
  API_BASE_URL: SERVER_ORIGIN + '/febs/api/',
  IMAGE_BASE_URL: SERVER_ORIGIN + '/imagesWeb/',
  UPLOAD_URL: SERVER_ORIGIN + '/febs/file/fileUpload'
};
