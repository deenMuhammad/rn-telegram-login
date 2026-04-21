import Foundation

@objc(RNTelegramLogin)
class RNTelegramLogin: NSObject {

    @objc
    func configure(
        _ clientId: String,
        redirectUri: String,
        scopes: [String],
        fallbackScheme: String?,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        DispatchQueue.main.async {
            TelegramLogin.configure(
                clientId: clientId,
                redirectUri: redirectUri,
                scopes: scopes,
                fallbackScheme: fallbackScheme
            )
            resolve(nil)
        }
    }

    @objc
    func login(
        _ resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        DispatchQueue.main.async {
            TelegramLogin.login { result in
                switch result {
                case .success(let data):
                    resolve(["idToken": data.idToken])
                case .failure(let error):
                    let telegramError = error as? TelegramLoginError
                    let code: String
                    switch telegramError {
                    case .cancelled:
                        code = "CANCELLED"
                    case .notConfigured:
                        code = "NOT_CONFIGURED"
                    case .noAuthorizationCode:
                        code = "NO_AUTH_CODE"
                    case .serverError:
                        code = "SERVER_ERROR"
                    case .requestFailed:
                        code = "REQUEST_FAILED"
                    case nil:
                        code = "UNKNOWN"
                    }
                    reject(code, error.localizedDescription, error)
                }
            }
        }
    }

    @objc
    func handleUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        DispatchQueue.main.async {
            TelegramLogin.handle(url)
        }
    }
}
