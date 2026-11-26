import { Link } from 'react-router'
import {
  LegalPageLayout,
  LegalSection,
  LegalTable,
  legalLinkClass,
  legalListClass,
} from '../components/legal/LegalPageLayout'

const headerCellClass = 'border-b border-line bg-[#EEF1EC] px-4 py-3 font-bold text-forest'
const cellClass = 'border-b border-line px-4 py-3 align-top'

export function CookiesPage() {
  return (
    <LegalPageLayout
      title="Cookies & Αποθήκευση"
      description="Πληροφορίες εργασίας για τα cookies, την αποθήκευση στον browser και τους εξωτερικούς πόρους που χρησιμοποιεί η τρέχουσα εφαρμογή."
    >
      <LegalSection title="Τι είναι τα cookies και η αποθήκευση στον browser">
        <p>
          Τα cookies είναι μικρές πληροφορίες που ένας ιστότοπος αποθηκεύει στο
          πρόγραμμα περιήγησης και λαμβάνει ξανά σε επόμενα αιτήματα. Άλλοι
          μηχανισμοί, όπως το localStorage, δεν είναι cookies και δεν
          αποστέλλονται αυτόματα με κάθε αίτημα, αλλά μπορούν να διατηρούν
          πληροφορίες στη συσκευή μεταξύ επισκέψεων.
        </p>
      </LegalSection>

      <LegalSection title="Cookies της εφαρμογής">
        <LegalTable>
          <thead>
            <tr>
              <th className={headerCellClass}>Όνομα</th>
              <th className={headerCellClass}>Σκοπός</th>
              <th className={headerCellClass}>Ρυθμισμένη διάρκεια</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className={cellClass}><code>accessToken</code></td>
              <td className={cellClass}>Αυθεντικοποίηση αιτημάτων προς το PawSpot.</td>
              <td className={cellClass}><code>Max-Age</code> 900 δευτερόλεπτα, δηλαδή 15 λεπτά.</td>
            </tr>
            <tr>
              <td className={cellClass}><code>refreshToken</code></td>
              <td className={cellClass}>Ανανέωση πρόσβασης, αποσύνδεση και ανάκληση συνεδρίας.</td>
              <td className={cellClass}><code>Max-Age</code> 604.800 δευτερόλεπτα, δηλαδή 7 ημέρες, σε κάθε έκδοση του cookie.</td>
            </tr>
            <tr>
              <td className={cellClass}><code>XSRF-TOKEN</code></td>
              <td className={cellClass}>Προστασία από πλαστογράφηση αιτημάτων μεταξύ ιστοτόπων (CSRF).</td>
              <td className={cellClass}>Cookie συνεδρίας, χωρίς επίμονο <code>Max-Age</code> για ενεργό token, βάσει της ρύθμισης του Spring Security.</td>
            </tr>
          </tbody>
        </LegalTable>
        <p>
          Οι πραγματικές ιδιότητες των cookies πρέπει να επαληθευτούν στον
          παραγωγικό ιστότοπο μέσω HTTPS.
        </p>
      </LegalSection>

      <LegalSection title="Σύνδεση και διάρκεια συνεδρίας">
        <p>
          Το <code>accessToken</code> χρησιμοποιείται για την αναγνώριση του
          συνδεδεμένου χρήστη. Το <code>refreshToken</code> μπορεί να εκδώσει νέο
          access token χωρίς νέα εισαγωγή κωδικού, εφόσον το token και η
          συνεδρία παραμένουν έγκυρα.
        </p>
        <p>
          Η διάρκεια ενός cookie στον browser διαφέρει από την εγκυρότητα του
          token και της συνεδρίας στον server. Η περιστροφή refresh token
          διατηρεί την αρχική ημερομηνία λήξης της επταήμερης συνεδρίας, ακόμη
          κι αν ο browser λάβει νέο cookie με επταήμερο <code>Max-Age</code>.
          Επομένως, η ανανέωση δεν παρέχει επτά επιπλέον ημέρες συνεδρίας.
        </p>
        <p>
          Τα δύο cookies αυθεντικοποίησης έχουν ρητά <code>HttpOnly=true</code>
          και <code>SameSite=Lax</code>. Το <code>accessToken</code> έχει διαδρομή
          <code> /</code> και το <code>refreshToken</code> διαδρομή
          <code> /api/auth</code>. Η παραγωγική ιδιότητα <code>Secure</code>
          χρειάζεται επαλήθευση μέσω HTTPS.
        </p>
      </LegalSection>

      <LegalSection title="Το cookie XSRF-TOKEN">
        <p>
          Το frontend διαβάζει το <code>XSRF-TOKEN</code> και στέλνει την τιμή
          στην κεφαλίδα <code>X-XSRF-TOKEN</code> για προστατευμένα αιτήματα. Δεν
          είναι cookie σύνδεσης.
        </p>
        <p>
          Το όνομα, το <code>HttpOnly=false</code>, η διάρκεια συνεδρίας και η
          διαδρομή προκύπτουν από τις προεπιλογές SPA του Spring Security και
          δεν ορίζονται από το cookie factory του PawSpot. Η τελική διαδρομή, το
          <code>Secure</code>, το <code>SameSite</code> και η συμπεριφορά του
          browser χρειάζονται επαλήθευση στην παραγωγή.
        </p>
      </LegalSection>

      <LegalSection title="localStorage">
        <p>
          Το frontend χρησιμοποιεί το ακριβές κλειδί
          <code> pawspot-auth-generation</code>. Η τιμή είναι ένα τυχαίο UUID που
          δείχνει ότι άλλαξε η κατάσταση αυθεντικοποίησης, ώστε να συντονίζονται
          αιτήματα και ανοιχτές καρτέλες. Δεν είναι access token ή refresh token
          και δεν αυθεντικοποιεί τον χρήστη.
        </p>
        <p>
          Η τιμή δημιουργείται ή αντικαθίσταται μετά από επιτυχή σύνδεση,
          αποσύνδεση ή διαγραφή λογαριασμού, καθώς και όταν αποτυχημένη ανανέωση
          με HTTP 401 ακυρώνει τη σύνδεση. Η εφαρμογή δεν ορίζει λήξη και δεν
          αφαιρεί αυτόματα το κλειδί. Παραμένει μέχρι να αντικατασταθεί ή να
          διαγραφούν τα δεδομένα ιστοτόπου.
        </p>
      </LegalSection>

      <LegalSection title="Cache και επικοινωνία μεταξύ καρτελών">
        <p>
          Το TanStack Query διατηρεί προσωρινά δεδομένα στη μνήμη. Δεν έχει
          ρυθμιστεί επίμονη αποθήκευση αυτής της cache. Το BroadcastChannel
          <code> pawspot-auth-state</code> και τα Web Locks με όνομα
          <code> pawspot-auth</code> συντονίζουν ζωντανές καρτέλες και ενέργειες
          αυθεντικοποίησης· δεν αποτελούν επίμονη αποθήκευση.
        </p>
        <p>
          Στον ελεγμένο frontend δεν βρέθηκε άλλη εφαρμογικά ορισμένη χρήση
          sessionStorage, IndexedDB, Cache API ή service worker.
        </p>
      </LegalSection>

      <LegalSection title="Επιλογές του browser">
        <p>
          Μπορείτε να διαγράψετε cookies και αποθηκευμένα δεδομένα του PawSpot
          από τις ρυθμίσεις του browser. Η διαγραφή των cookies αυθεντικοποίησης
          μπορεί να σας αποσυνδέσει ή να εμποδίσει την ανανέωση της σύνδεσης. Η
          διαγραφή του <code>XSRF-TOKEN</code> μπορεί να απαιτήσει έκδοση νέου
          token πριν από προστατευμένη ενέργεια.
        </p>
        <p>
          Η εκκαθάριση δεδομένων του browser δεν διαγράφει τον λογαριασμό
          PawSpot ή τα δεδομένα που τηρούνται στον server.
        </p>
      </LegalSection>

      <LegalSection title="Εξωτερικοί πόροι">
        <ul className={legalListClass}>
          <li>Η γραμματοσειρά Nunito περιλαμβάνεται στο frontend και παρέχεται από την ίδια την εφαρμογή, χωρίς εισαγωγή Google Fonts.</li>
          <li>Οι φωτογραφίες αγγελιών αποθηκεύονται και παραδίδονται μέσω Cloudinary.</li>
          <li>Δεν βρέθηκε ενσωμάτωση analytics ή διαφήμισης στον ελεγμένο πηγαίο κώδικα.</li>
        </ul>
        <p>
          Τα παραπάνω δεν αποτελούν πλήρη έλεγχο παραγωγικών αιτημάτων που
          μπορεί να εισάγονται από hosting, proxy, CDN ή runtime ρυθμίσεις.
        </p>
      </LegalSection>

      <LegalSection title="Συγκατάθεση και τεχνική αναγκαιότητα">
        <p>
          Οι τεχνικοί σκοποί των μηχανισμών είναι η αυθεντικοποίηση, η ανανέωση
          συνεδρίας, η προστασία CSRF και ο συντονισμός αλλαγών σύνδεσης. Η
          τελική νομική κατάταξη κάθε cookie και του localStorage ως προς τη
          συγκατάθεση ή την εξαίρεση τεχνικής αναγκαιότητας είναι υπό
          οριστικοποίηση. Δεν χαρακτηρίζεται αυτομάτως κάθε μηχανισμός ως
          εξαιρούμενος.
        </p>
      </LegalSection>

      <LegalSection title="Σχετικές πληροφορίες">
        <p>
          Δείτε την <Link to="/privacy" className={legalLinkClass}>Πολιτική Απορρήτου</Link>{' '}
          και τους <Link to="/terms" className={legalLinkClass}>Όρους Χρήσης</Link>.
        </p>
      </LegalSection>
    </LegalPageLayout>
  )
}
