
function PrintOrder() {
    cordova.exec(successCallback, errorCallback, "AABBluetooth", "Print", [MerchantTxt, CustomerTxt, OrderTxt, PaymentTxt]);
}
function successCallback(response) {

}
function errorCallback(error, error1, error2) {
    onsenAlert(error);
}