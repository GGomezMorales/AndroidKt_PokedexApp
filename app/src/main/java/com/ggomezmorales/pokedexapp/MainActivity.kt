package com.ggomezmorales.pokedexapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ggomezmorales.pokedexapp.ui.theme.PokedexAppTheme
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

// 1. Entities/Domain
data class Pokemon(
    val id: Int,
    val name: String,
    val isFavorite: Boolean = false
)

// 2. DataSources/UseCases
interface GetPokemonDataSource {
    suspend fun getPokemonList(): List<Pokemon>
}

interface SavePokemonDataSource {
    fun savePokemon(pokemon: Pokemon)
}

interface PokemonDataSource : GetPokemonDataSource, SavePokemonDataSource

class GetPokemonMockDataSourceImpl : GetPokemonDataSource {

    private val pokemonMockList = listOf(
        Pokemon(1, "Pikachu"),
        Pokemon(2, "Charmander"),
        Pokemon(3, "Squirtle"),
        Pokemon(4, "Bulbasaur"),
    )

    override suspend fun getPokemonList(): List<Pokemon> = pokemonMockList

}

////////////////////////////// ROOM //////////////////////////////
@Entity(tableName = "pokemon_favorites")
data class PokemonEntity(
    @PrimaryKey val id: Int?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "type") val type: String?
)

@Dao
interface PokemonFavoritesDao {
    @Query("SELECT * FROM pokemon_favorites")
    fun getAll(): List<PokemonEntity>

    @Query("SELECT * FROM pokemon_favorites WHERE id IN (:userIds)")
    fun loadAllByIds(userIds: IntArray): List<PokemonEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg users: PokemonEntity)

    @Delete
    fun delete(user: PokemonEntity)
}

@Database(entities = [PokemonEntity::class], version = 3)
abstract class PokemonDatabase : RoomDatabase() {
    abstract fun pokemonFavoritesDao(): PokemonFavoritesDao
}

class PokemonFavoritesDatasourceImpl(
    private val applicationContext: Context
) : PokemonDataSource {

    private val db = Room.databaseBuilder(
        applicationContext,
        PokemonDatabase::class.java, "pokemon_favorites_db"
    ).build()

    private val pokemonDao = db.pokemonFavoritesDao()

    override suspend fun getPokemonList(): List<Pokemon> = pokemonDao.getAll().map {
        it.toPokemon()
    }

    override fun savePokemon(pokemon: Pokemon) {
        pokemonDao.insertAll(
            pokemon.toPokemonEntity()
        )
    }

}

fun PokemonEntity.toPokemon() = Pokemon(
    id = id ?: 0,
    name = name ?: ""
)

fun Pokemon.toPokemonEntity() = PokemonEntity(
    id = id,
    name = name,
    type = ""
)

////////////////////////////// RETROFIT //////////////////////////////

data class PokemonResponseDTO(
    @Json(name = "count") val count: Int,
    @Json(name = "next") val next: String?,
    @Json(name = "previous") val previous: String?,
    @Json(name = "results") val pokemonList: List<PokemonDTO>
)

data class PokemonDTO(
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String
)

interface PokemonApi {

    @GET("pokemon")
    suspend fun getPokemonList(): Response<PokemonResponseDTO>

}

class GetPokemonDataSourceImpl : GetPokemonDataSource {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://pokeapi.co/api/v2/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val pokemonApi: PokemonApi = retrofit.create(PokemonApi::class.java)

    override suspend fun getPokemonList(): List<Pokemon> {
        val pokemonResponse = pokemonApi.getPokemonList()
        return pokemonResponse.body()?.pokemonList?.map { pokemonDTO ->
            pokemonDTO.toPokemon()
        } ?: emptyList()
    }

}

fun PokemonDTO.toPokemon() = Pokemon(
    id = url.split("/").last { it.isNotBlank() }.toInt(),
    name = name
)

////////////////////////////// END RETROFIT //////////////////////////////

// 3. Repository
interface PokemonRepository {
    suspend fun getPokemonList(): List<Pokemon>
}

interface PokemonFavoritesRepository {
    suspend fun getPokemonList(): List<Pokemon>
    fun savePokemon(pokemon: Pokemon)
}

class PokemonRepositoryImpl(
    private val getPokemonDataSource: GetPokemonDataSource = GetPokemonDataSourceImpl(), // TODO: FIX THIS WHIT HILT
) : PokemonRepository {

    override suspend fun getPokemonList(): List<Pokemon> {
        return getPokemonDataSource.getPokemonList()
    }
}

class PokemonFavoritesRepositoryImpl(
    private val context: Context, // TODO: TEMPORAL SOLUTION BEFORE HILT
    private val pokemonDataSource: PokemonDataSource = PokemonFavoritesDatasourceImpl(context)
) : PokemonFavoritesRepository {

    override suspend fun getPokemonList(): List<Pokemon> {
        return pokemonDataSource.getPokemonList()
    }

    override fun savePokemon(pokemon: Pokemon) {
        pokemonDataSource.savePokemon(pokemon)
    }
}

// 4. Presenter/ViewModel
class PokemonViewModel : ViewModel() {

    // TODO: FIX THIS WITH HILT
    private val pokemonRepository: PokemonRepository = PokemonRepositoryImpl()
    private var pokemonFavoritesRepository: PokemonFavoritesRepository? = null

    private val _pokemonListState = MutableStateFlow(listOf<Pokemon>())
    val pokemonListState = _pokemonListState.asStateFlow()

    private val _pokemonFavoriteListState = MutableStateFlow(listOf<Pokemon>())
    val pokemonFavoriteListState = _pokemonFavoriteListState.asStateFlow()

    fun getPokemonList() {
        viewModelScope.launch(Dispatchers.IO) {
            _pokemonListState.value = pokemonRepository.getPokemonList()
            _pokemonFavoriteListState.value =
                pokemonFavoritesRepository?.getPokemonList() ?: emptyList()
        }
    }

    fun markAsFavorite(pokemon: Pokemon) {
        viewModelScope.launch(Dispatchers.IO) {
            pokemonFavoritesRepository?.savePokemon(pokemon)
            pokemonFavoritesRepository?.getPokemonList()?.let {
                _pokemonFavoriteListState.value = it
            }
        }
    }

    // TODO: TEMPORAL SOLUTION BEFORE HILT
    fun initDependencies(context: Context) {
        pokemonFavoritesRepository = PokemonFavoritesRepositoryImpl(context)
    }
}

// 4. Presenter/UI
class MainActivity : ComponentActivity() {

    private val pokemonViewModel by viewModels<PokemonViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pokemonViewModel.initDependencies(this)
        setContent {
            LaunchedEffect(Unit) {
                pokemonViewModel.getPokemonList()
            }
            val pokemonListState by pokemonViewModel.pokemonListState.collectAsState()
            val pokemonFavoriteListState by pokemonViewModel.pokemonFavoriteListState.collectAsState()
            PokedexAppTheme {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(modifier = Modifier.fillMaxWidth()) {
                        items(pokemonFavoriteListState) {
                            PokemonFavorite(pokemon = it)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(pokemonListState) { pokemon ->
                            PokemonRow(
                                onLikeClick = { pokemonClicked ->
                                    pokemonViewModel.markAsFavorite(pokemonClicked)
                                },
                                isLiked = pokemonFavoriteListState.contains(pokemon), // TODO: USED ONLY FOR TEACH PURPOSES (AVOID)
                                pokemon = pokemon,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun PokemonFavorite(
    pokemon: Pokemon = Pokemon(0, "Pikachu")
) {
    Text(
        modifier = Modifier.padding(8.dp),
        text = pokemon.name
    )
}


@Preview
@Composable
fun PokemonRow(
    pokemon: Pokemon = Pokemon(0, "Pikachu"),
    isLiked: Boolean = false,
    onLikeClick: (Pokemon) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            modifier = Modifier.align(Alignment.CenterStart),
            text = pokemon.name
        )
        IconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { onLikeClick(pokemon) }
        ) {
            Icon(
                modifier = Modifier,
                imageVector = Icons.Sharp.Favorite,
                contentDescription = "like",
                tint = if (isLiked) Color.Red else Color.Gray
            )
        }
    }
}