/**
 * Main GuestBook class
 * Used for all GB-related actions: adding, removal and listing
 * @author Alex Chernyshev <mailto:alex3.145@gmail.com>
 */
class GuestBook {
    /**
     * Adds new DOM element with GB record:
     * makes clone from template block,then substitutes with values.
     * @param {*} messagesEl 
     * @param {*} record 
     * @param {*} prepend 
     */
    addRecordFromTemplate(messagesEl, record, prepend) {
        // create new element, cloned from template
        let cloneEl = document.querySelector('#message-template').cloneNode(true);
        // set id
        cloneEl.setAttribute('id', 'id_' + record.id);
        // inject content
        cloneEl.querySelector('#message-title').innerHTML = record.title;
        cloneEl.querySelector('#message-text').innerHTML = record.message;
        cloneEl.querySelector('#message-author').innerHTML = record.author;
        cloneEl.querySelector('#message-date').innerText = new Date(parseInt(record.created)).toLocaleString();
        // and action buttons
        const deleteBtn = cloneEl.querySelector('#deleteBtn');
        // delete button will not exist on page, if user is not authenticated 
        if (deleteBtn) { deleteBtn.addEventListener('click', (e) => { e.preventDefault();
                // 'confirm' message will be extracted from attribute
                if (window.confirm(deleteBtn.getAttribute('confirm'))) { gb.deleteRecord(record.id); }
            });
        }
        // append to the end or prepend to top
        if (prepend) { messagesEl.insertBefore(cloneEl, messagesEl.firstChild); } else { messagesEl.appendChild(cloneEl); }
        // and finally show new element
        cloneEl.style.display = '';
    }
    /**
     * Submit new guest book record to server
     */
    addRecord() {
        // bind current instance to variable, to access other methods of same class from listener callbacks
        const self = this;
        // extract form fields
        let author = self.escapeHTML(document.querySelector('#authorInput').value);
        let title = self.escapeHTML(document.querySelector('#titleInput').value);
        let message = self.escapeHTML(document.querySelector('#messageInput').value);
        // make API call to add new post
        fetch('/api/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: title, author: author, message: message })
        }).then(response => {
            // if there was an error - show the alert block
            // and throw exception
            if (!response.ok) {
                document.querySelector('#errorAlert').style.display = '';
                throw new Error(response.statusText);
            } else { document.querySelector('#errorAlert').style.display = 'none'; }
            // respond parsed json, if added successfully
            // json will contain our newly created record
            return response.json();
        }).then(record => {
            // if API call was successful - reset form and add created record to list, without page reload
            console.log('new record:', record);
            document.querySelector('#newRecordForm').reset();
            self.addRecordFromTemplate(document.querySelector('#messages'), record, true);
        }).catch(error => { console.log("cannot add: ", error); });
    }
    /**
     * Loads GB records from server
     */
    loadRecords() {
        // create new constant that points to 'this', to obtain self instance inside handlers (see below)
        const self = this;
        // make API call to retrieve list of GB records
        fetch('/api/records', { method: 'GET', headers: {} }).then(response => response.json()).then(records => {
            console.log('loaded records:', records);
            // get 'messages' element on page and reset its inner contents
            let messagesEl = document.querySelector('#messages'); messagesEl.innerHTML = "";
            // add each GB record, found in JSON to page, using record template
            records.forEach(record => { self.addRecordFromTemplate(messagesEl, record, false); });
        }).catch(error => { console.log("cannot add: ", error); });
    }
    /**
     * Change language
     * 
     * @param lang
                'ru' - to switch to Russian
                'en' - to English
     */
    changeLang(lang) {
            console.log("change lang to: ", lang);
            fetch('/api/locale?' + new URLSearchParams({ lang: lang }), { method: 'POST', headers: {} }).then((response) => {
                // support for redirection
                if (response.redirected) { location.href = response.url; }
            }).catch(error => { console.log("error on lang select: ", error); });
        }
    /**
    * Deletes GB record
    *
    * @param recordId
                guestbook record's id
    */
    deleteRecord(recordId) {
        console.log("removing record: ", recordId);
        fetch('/api/delete?' + new URLSearchParams({ id: recordId }), { method: 'POST', headers: {} }).then((response) => {
            if (response.ok) { gb.loadRecords(); }
        }).catch(error => { console.log("error on remove record: ", error); });
    }
    /**
      Very simple escaping for special characters
    */
    escapeHTML(unsafe) { return unsafe.replace(/[\u0000-\u002F\u003A-\u0040\u005B-\u0060\u007B-\u00FF]/g,
        c => '&#' + ('000' + c.charCodeAt(0)).slice(-4) + ';');
    }
}
// create class instance
const gb = new GuestBook();
// add event handlers on page load
window.onload = () => {
    // add 'click' event handler to 'submit' button
    document.querySelector('#submitBtn').addEventListener('click', (e) => { e.preventDefault(); gb.addRecord(); });
    // add form submit handler (when user press 'enter' in form)
    document.querySelector('#newRecordForm').addEventListener('submit', (e) => { e.preventDefault(); gb.addRecord(); });
    // add 'click' event handler to toggle language to English
    document.querySelector('#selectEn').addEventListener('click', (e) => {e.preventDefault(); gb.changeLang('en'); });
    // .. to Russian
    document.querySelector('#selectRu').addEventListener('click', (e) => {e.preventDefault(); gb.changeLang('ru'); });
    // and finally - call to load GB records
    gb.loadRecords();
};