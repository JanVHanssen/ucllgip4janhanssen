package be.ucll.ucllgip4janhanssen;


import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Fragment waar er een nieuwe chatgroep kan aangemaakt worden
public class GroupchatFragment extends Fragment {

    private EditText editTextGroupName;
    private Button buttonCreate;
    private RecyclerView recyclerViewContacts;
    private GroupChatAdapter groupChatAdapter;
    private List<Contact> contactsList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groupchat, container, false);

        // Initialize views
        editTextGroupName = view.findViewById(R.id.edit_text_group_name);
        buttonCreate = view.findViewById(R.id.button_create);
        recyclerViewContacts = view.findViewById(R.id.recycler_view_contacts);

        // Set up RecyclerView
        recyclerViewContacts.setLayoutManager(new LinearLayoutManager(getActivity()));
        contactsList = new ArrayList<>();
        groupChatAdapter = new GroupChatAdapter(contactsList);
        recyclerViewContacts.setAdapter(groupChatAdapter);

        fetchContactsFromFirestore();

        buttonCreate.setOnClickListener(v -> {
            String groupName = editTextGroupName.getText().toString().trim();
            if (!groupName.isEmpty()) {
                saveGroupNameToFirestore(groupName);
            } else {
                Toast.makeText(getActivity(), "Group name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle("Groups");
        }

        return view;
    }

    // De contacts ophalen van de huidige ingelogde gebruiker
    private void fetchContactsFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentUserPhoneNumber = getCurrentUserPhoneNumber();

        db.collection("users")
                .document(currentUserPhoneNumber)
                .collection("contacts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        Contact contact = documentSnapshot.toObject(Contact.class);
                        contactsList.add(contact);
                    }
                    groupChatAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    // Handle errors
                });
    }

    // De groep aanmaken in de collectie groupchats in firestore
    private void saveGroupNameToFirestore(String groupName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference groupChatRef = db.collection("groupchats").document(groupName);

        Map<String, Object> groupData = new HashMap<>();
        groupData.put("name", groupName);

        groupChatRef.set(groupData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Group chat created successfully");

                    // Standardize phone numbers of contacts
                    for (Contact contact : contactsList) {
                        contact.setPhoneNumber(standardizePhoneNumber(contact.getPhoneNumber()));
                    }

                    for (Contact contact : contactsList) {
                        if (contact.isChecked()) {
                            addContactToGroupChat(db, groupName, contact);
                        }
                    }
                    addLoggedInUserToGroupChat(db, groupName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating group chat", e);
                });
    }

    // De huidige gebruiker toevoegen aan de groepchat
    private void addLoggedInUserToGroupChat(FirebaseFirestore db, String groupName) {

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser loggedInUser = auth.getCurrentUser();

        if (loggedInUser != null) {
            String phoneNumber = loggedInUser.getPhoneNumber();

            Contact loggedInContact = new Contact();
            loggedInContact.setPhoneNumber(phoneNumber);

            db.collection("groupchats").document(groupName).collection("users").document(phoneNumber)
                    .set(loggedInContact)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Logged-in user added to group chat");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error adding logged-in user to group chat", e));
        } else {
            Log.e(TAG, "Current user is null");
        }
    }

    // Alle aangevinkte gebruikers toevoegen aan de groepschat
    private void addContactToGroupChat(FirebaseFirestore db, String groupName, Contact contact) {
        String contactPhoneNumber = contact.getPhoneNumber();

        db.collection("groupchats").document(groupName).collection("users").document(contactPhoneNumber)
                .set(contact)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getActivity(), "Contact added to group chat", Toast.LENGTH_SHORT).show();
                    navigateToContactsFragment();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding contact to group chat", e));
    }

    // Navigeren naar het contacts fragment
    private void navigateToContactsFragment() {
        NavHostFragment.findNavController(this).navigate(R.id.action_groupchat_to_contacts);
    }

    // Telefoon nummer ophalen van de huidige gebruiker
    private String getCurrentUserPhoneNumber() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getPhoneNumber();
        } else {
            return null;
        }
    }

    // Methode om de telefoonnummers op dezelfde schrijfwijze te hebben
    private String standardizePhoneNumber(String phoneNumber) {
        // Remove all non-numeric characters from the phone number
        String standardizedNumber = phoneNumber.replaceAll("[^0-9]", "");
        // Add country code if missing (assuming a country code of "+1" for example)
        if (!standardizedNumber.startsWith("+")) {
            standardizedNumber = "+" + standardizedNumber;
        }
        return standardizedNumber;
    }
}