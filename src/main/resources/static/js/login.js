/**
 * User authentication support
 * 
 * @author Alex Chernyshev <mailto:alex3.145@gmail.com> 
 */
class GuestBookLogin {
    /**
     * Submit entered credentials to server
     */
    doAuth() {
        fetch('/api/auth', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: document.querySelector('#usernameInput').value,
                password: document.querySelector('#passwordInput').value })
        }).then((response) => {
            // if there was an error
            if (!response.ok) {
                // reset form
                document.querySelector('#loginForm').reset();
                // show generic alert
                document.querySelector('#errorAlert').style.display = '';
                // if authentication successful - there must be redirect to first page
            } else if (response.redirected) {
                location.href = response.url;
            }
        }).catch(error => { console.log("auth error: ", error); });
    }
}
// create class instance
const gbLogin = new GuestBookLogin();
// add listeners on page load
window.onload = () => {
    // add 'click' event handler to login button
    document.querySelector('#loginBtn').addEventListener('click', (e) => { e.preventDefault(); gbLogin.doAuth(); });
    // add form submit handler (when you press 'enter' key)
    document.querySelector('#loginForm').addEventListener('submit', (e) => { e.preventDefault(); gbLogin.doAuth(); });
};