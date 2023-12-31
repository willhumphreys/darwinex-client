package uk.co.threebugs.darwinexclient.accountsetupgroups

import jakarta.persistence.*
import org.hibernate.envers.*
import uk.co.threebugs.darwinexclient.account.*
import uk.co.threebugs.darwinexclient.setupgroups.*

@Entity
@Table(name = "account_setup_groups")
@Audited
class AccountSetupGroups(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    var name: String? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    var account: Account? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "setup_groups_id")
    var setupGroups: SetupGroups? = null
)
