package com.ggomezmorales.pokedexapp

import com.ggomezmorales.pokedexapp.ui.theme.PokedexAppTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

// Entities/Domain
data class Pokemon(
    val id: Int,
    val name: String
)

// DataSources
interface GetPokemonDataSource {
    suspend fun getPokemonList(): List<Pokemon>
}

class GetPokemonMockDataSourceImpl : GetPokemonDataSource {

    private val pokemonMockList = listOf(
        Pokemon(1, "Pikachu"),
        Pokemon(2, "Charmander"),
        Pokemon(3, "Squirtle"),
        Pokemon(4, "Bulbasaur"),
    )

    override suspend fun getPokemonList(): List<Pokemon> = pokemonMockList

}

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
    id = 0,
    name = name
)

////////////////////////////// END RETROFIT //////////////////////////////

interface SavePokemonDataSource {
    fun savePokemon(pokemon: Pokemon)
}

// Repository
interface PokemonRepository {
    suspend fun getPokemonList(): List<Pokemon>
    fun savePokemon(pokemon: Pokemon)
}

class PokemonRepositoryImpl(
    // TODO: FIX THIS WHIT HILT
    private val getPokemonDataSource: GetPokemonDataSource = GetPokemonDataSourceImpl()
) : PokemonRepository {

    override suspend fun getPokemonList(): List<Pokemon> {
        return getPokemonDataSource.getPokemonList()
    }

    override fun savePokemon(pokemon: Pokemon) {

    }
}

// ViewModel
class PokemonViewModel : ViewModel() {

    // TODO: FIX THIS WITH HILT
    private val pokemonRepository: PokemonRepository = PokemonRepositoryImpl()

    private val _pokemonListState = MutableStateFlow(listOf<Pokemon>())
    val pokemonListState = _pokemonListState.asStateFlow()

    fun getPokemonList() {
        viewModelScope.launch {
            _pokemonListState.value = pokemonRepository.getPokemonList()
        }
    }

    fun savePokemon(pokemon: Pokemon) {
        pokemonRepository.savePokemon(pokemon)
    }
}

// UI
class MainActivity : ComponentActivity() {

    private val pokemonViewModel by viewModels<PokemonViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                pokemonViewModel.getPokemonList()
            }
            val pokemonListState by pokemonViewModel.pokemonListState.collectAsState()
            PokedexTheme {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(pokemonListState) { pokemon ->
                        Text(pokemon.name)
                    }
                }
            }
        }
    }
}